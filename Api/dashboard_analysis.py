"""Verified Databricks custom serving integration; no LLM-text-to-number fallback."""
import os
from concurrent.futures import ThreadPoolExecutor
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
    @staticmethod
    def load_configuration():
        path = os.getenv('DASHBOARD_MODEL_CONFIG')
        if not path:
            return None, 'Analysis is not configured. Ask the service operator to install a verified workflow.'
        try:
            return Deployment.model_validate_json(Path(path).read_text()), None
        except (OSError, ValueError):
            return None, 'Analysis configuration is invalid. Ask the service operator to repair it.'

    def readiness(self):
        """Configuration preflight only: never implies that source data or inference is ready."""
        config, reason = self.load_configuration()
        credentials = os.getenv('DATABRICKS_HOST', '').startswith('https://') and bool(os.getenv('DATABRICKS_TOKEN'))
        results = {}
        for key in METRICS:
            policy = config.metrics.get(key) if config else None
            issue = reason
            if config and not policy:
                issue = 'This analysis has no verified workflow. Ask the service operator to configure it.'
            elif policy and not credentials:
                issue = 'Analysis service access is missing. Ask the service operator to restore it.'
            results[key] = {'configured': issue is None, 'reason': issue,
                'model_version': config.entity_version if policy else None,
                'requirements': policy.model_dump(mode='json') if policy else None}
        return {'contract_version': '1', 'results': results}

    def analyze(self, request: DashboardRequest) -> DashboardResponse:
        wid = request.source_window.id
        config, reason = self.load_configuration()
        results = {key: MetricResult.unavailable(reason or 'This analysis has no verified workflow. Ask the service operator to configure it.', wid) for key in METRICS}
        if config:
            supported = {}
            for key, policy in config.metrics.items():
                reason = self.missing_inputs(request, key, policy)
                if reason:
                    results[key] = MetricResult.unavailable(reason, wid)
                else:
                    supported[key] = policy

            def analyze_metric(item):
                key, policy = item
                # A workflow receives only this eligible metric and its required sensors.
                isolated = config.model_copy(update={'metrics': {key: policy}})
                try:
                    raw = self.invoke(isolated, request)
                except Exception:
                    return key, MetricResult.unavailable('Analysis service request failed. Try syncing again.', wid)
                try:
                    return key, self.validate_output(raw.get(key), policy, config, wid)
                except (ValueError, TypeError, KeyError, AttributeError, StopIteration):
                    return key, MetricResult.unavailable('The model returned an invalid result for this metric.', wid)

            if supported:
                # Concurrent calls keep the total budget bounded by one inference timeout,
                # rather than multiplying Android's wait by the number of supported metrics.
                with ThreadPoolExecutor(max_workers=len(supported)) as workers:
                    results.update(workers.map(analyze_metric, supported.items()))
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
            return 'Source timestamp alignment is unverified. Ask the data provider to certify timezone and synchronization.'
        if policy.requires_meals and not request.meals:
            return 'Required meal context is missing. Load the associated meal recording.'
        for sensor, required in policy.required_sensors.items():
            series = request.sensors.get(sensor)
            if series is None or len(series.readings) < required.min_samples:
                return f'Required {sensor.upper()} coverage is missing or insufficient. Load a complete overlapping recording.'
            if series.unit != required.unit:
                return f'Required {sensor.upper()} units have not been verified. Confirm the source export units.'
            times = [request.source_window.start] + [r.timestamp for r in series.readings] + [request.source_window.end]
            if any((b-a).total_seconds() > required.max_gap_seconds for a,b in zip(times,times[1:])):
                return f'Required {sensor.upper()} coverage has a gap or ends too early. Load a complete overlapping recording.'
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
            inputs['requested_metrics'] = list(config.metrics)
            required = {sensor for policy in config.metrics.values() for sensor in policy.required_sensors}
            inputs['sensors'] = {sensor: series for sensor, series in inputs['sensors'].items() if sensor in required}
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
