"""Verified Databricks custom serving integration; no LLM-text-to-number fallback."""
import os
from pathlib import Path
from urllib.parse import quote
import httpx
from pydantic import Field, FiniteFloat, model_validator
if __package__:
    from .contracts import Contract, DashboardRequest, DashboardResponse, MetricResult, ReferenceRange, Category, Sensor, METRICS
else:
    from contracts import Contract, DashboardRequest, DashboardResponse, MetricResult, ReferenceRange, Category, Sensor, METRICS

class Band(Contract):
    lower: FiniteFloat | None = None  # inclusive
    upper: FiniteFloat | None = None  # exclusive
    category: Category
    bar_position: FiniteFloat = Field(ge=0, le=1)

class InputRequirement(Contract):
    unit: str
    min_samples: int = Field(ge=2)
    max_gap_seconds: FiniteFloat = Field(gt=0)

class MetricPolicy(Contract):
    unit: str
    threshold_version: str = Field(min_length=1)
    reference_range: ReferenceRange
    bands: list[Band] = Field(min_length=1)
    required_sensors: dict[Sensor, InputRequirement] = Field(min_length=1)
    requires_meals: bool = False
    confidence_validated: bool = False
    supports_postmeal_spikes: bool = False

    @model_validator(mode='after')
    def validated(self):
        if self.reference_range.unit != self.unit:
            raise ValueError('Policy units disagree')
        if self.bands[0].lower is not None or self.bands[-1].upper is not None:
            raise ValueError('Bands must cover all numeric outputs')
        for i, band in enumerate(self.bands):
            if band.lower is not None and band.upper is not None and band.lower >= band.upper:
                raise ValueError('Invalid threshold band')
            if i and (band.lower is None or self.bands[i-1].upper != band.lower):
                raise ValueError('Threshold bands must be ordered and contiguous')
        return self

class Deployment(Contract):
    endpoint: str = Field(min_length=1, pattern=r'^[a-zA-Z0-9_.-]+$')
    entity_name: str = Field(min_length=1)
    entity_version: str = Field(min_length=1)
    # Exported from associated model configuration or threshold table, never UI constants.
    configuration_source: str = Field(min_length=1)
    metrics: dict[str, MetricPolicy]

    @model_validator(mode='after')
    def supported(self):
        if not set(self.metrics) <= set(METRICS):
            raise ValueError('Unknown metric configuration')
        return self

class DatabricksDashboardAnalyzer:
    def analyze(self, request: DashboardRequest) -> DashboardResponse:
        wid = request.source_window.id
        results = {key: MetricResult.unavailable('No verified dashboard prediction workflow is configured.', wid) for key in METRICS}
        config_path = os.getenv('DASHBOARD_MODEL_CONFIG')
        if config_path:
            try:
                config = Deployment.model_validate_json(Path(config_path).read_text())
                supported = {}
                for key, policy in config.metrics.items():
                    reason = self.missing_inputs(request, key, policy)
                    if reason:
                        results[key] = MetricResult.unavailable(reason, wid)
                    else:
                        supported[key] = policy
                if supported:
                    raw = self.invoke(config, request)
                    # Every card is validated independently; malformed siblings cannot erase good output.
                    for key, policy in supported.items():
                        try:
                            results[key] = self.validate_output(raw.get(key), policy, config, wid)
                        except (ValueError, TypeError, KeyError):
                            results[key] = MetricResult.unavailable('The model returned an invalid result for this metric.', wid)
            except Exception:
                # Do not expose credentials, raw API responses, or model text on the dashboard.
                for key in METRICS:
                    if results[key].reason == 'No verified dashboard prediction workflow is configured.':
                        results[key] = MetricResult.unavailable('The verified analysis service is unavailable.', wid)
        return DashboardResponse(patient_id=request.patient_id, session_id=request.session_id,
            interval=request.interval, window_id=wid, results=results)

    @staticmethod
    def missing_inputs(request, key, policy):
        # These are structural minimums, not medical reference thresholds.
        minimums = {'hr_eda': {'hr', 'eda'}, 'glucose_variability': {'glucose'},
                    'hrv': {'ibi'}, 'prediabetes_risk': {'hr', 'eda', 'acc', 'temp', 'ibi', 'glucose'}}
        if not minimums[key] <= policy.required_sensors.keys():
            return 'The model input configuration is incomplete.'
        if request.source_window.time_basis != 'verified_utc':
            return 'Source timestamp alignment has not been verified.'
        if policy.requires_meals and not request.meals:
            return 'Required meal context is missing.'
        for sensor, required in policy.required_sensors.items():
            series = request.sensors.get(sensor)
            if series is None or len(series.readings) < required.min_samples:
                return f'Required {sensor.upper()} coverage is missing or insufficient.'
            if series.unit != required.unit:
                return f'Required {sensor.upper()} units have not been verified.'
            times = [request.source_window.start] + [r.timestamp for r in series.readings] + [request.source_window.end]
            if any((b-a).total_seconds() > required.max_gap_seconds for a,b in zip(times,times[1:])):
                return f'Required {sensor.upper()} coverage has a gap or ends too early.'
        return None

    @staticmethod
    def invoke(config, request):
        host = os.getenv('DATABRICKS_HOST', '').rstrip('/')
        token = os.getenv('DATABRICKS_TOKEN', '')
        if not host.startswith('https://') or not token:
            raise ValueError('Missing Databricks credentials')
        timeout = float(os.getenv('DASHBOARD_INFERENCE_TIMEOUT_SECONDS', '120'))
        with httpx.Client(headers={'Authorization': f'Bearer {token}'}, timeout=httpx.Timeout(timeout, connect=10), follow_redirects=False) as client:
            url = host + '/api/2.0/serving-endpoints/' + quote(config.endpoint, safe='')
            meta = client.get(url, timeout=10)
            meta.raise_for_status()
            endpoint = meta.json()
            entities = endpoint.get('config', {}).get('served_entities', [])
            if endpoint.get('state', {}).get('ready') != 'READY' or len(entities) != 1:
                raise ValueError('Model deployment is not ready or routes across multiple versions')
            entity = entities[0]
            if entity.get('entity_name') != config.entity_name or str(entity.get('entity_version')) != config.entity_version or entity.get('type') == 'FOUNDATION_MODEL':
                raise ValueError('Endpoint does not serve the verified model version')
            # All source windows/features/context go to the deployed workflow, never looped averages.
            inputs = request.model_dump(mode='json', exclude={'cumulative_replay_averages'})
            response = client.post(host + '/serving-endpoints/' + quote(config.endpoint, safe='') + '/invocations', json={'inputs': [inputs]})
            response.raise_for_status()
            predictions = response.json()['predictions']
            if len(predictions) != 1:
                raise ValueError('Expected a single dashboard prediction')
            return predictions[0]['results']

    @staticmethod
    def validate_output(raw, policy, config, window_id):
        result = MetricResult.model_validate(raw)
        if result.window_id != window_id:
            raise ValueError('Model result belongs to a different window')
        if result.availability == 'unavailable':
            return result
        if result.model_version != config.entity_version or result.unit != policy.unit or result.threshold_version != policy.threshold_version or result.reference_range != policy.reference_range:
            raise ValueError('Model result does not match verified configuration')
        band = next(b for b in policy.bands if (b.lower is None or result.value >= b.lower) and (b.upper is None or result.value < b.upper))
        if result.risk_category != band.category or result.bar_position != band.bar_position:
            raise ValueError('Category and bar position disagree with thresholds')
        if result.confidence is not None and not policy.confidence_validated:
            raise ValueError('Confidence is not validated by this model')
        if result.supports_postmeal_spikes and not policy.supports_postmeal_spikes:
            raise ValueError('Postmeal interpretation is not supported')
        return result
