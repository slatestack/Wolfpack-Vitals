"""Dashboard v1: source timestamps are never synthesized or independently replayed."""
from datetime import datetime
from typing import Literal, Annotated
from pydantic import BaseModel, ConfigDict, Field, model_validator

FiniteFloat = Annotated[float, Field(strict=True, allow_inf_nan=False)]

METRICS = ('hr_eda', 'glucose_variability', 'hrv', 'prediabetes_risk')
Sensor = Literal['hr', 'eda', 'acc', 'temp', 'ibi', 'glucose']
Category = Literal['typical', 'low', 'moderate', 'elevated', 'high']

class Contract(BaseModel):
    model_config = ConfigDict(extra='forbid')

class SourceWindow(Contract):
    id: str = Field(min_length=1, max_length=200)
    start: datetime
    end: datetime
    time_basis: Literal['verified_utc', 'source_local_unspecified']

    @model_validator(mode='after')
    def ordered(self):
        if (self.start.tzinfo is None) != (self.end.tzinfo is None) or self.end <= self.start:
            raise ValueError('Source window must have consistent, increasing timestamps')
        if self.time_basis == 'verified_utc' and (self.start.utcoffset() is None or self.start.utcoffset().total_seconds() != 0 or self.end.utcoffset().total_seconds() != 0):
            raise ValueError('Verified UTC windows require explicit UTC offsets')
        if self.time_basis == 'source_local_unspecified' and self.start.tzinfo is not None:
            raise ValueError('Unspecified local timestamps must not invent an offset')
        return self

class Reading(Contract):
    timestamp: datetime
    values: list[FiniteFloat] = Field(min_length=1, max_length=3)

class Coverage(Contract):
    sample_count: int = Field(ge=0)
    first_timestamp: datetime | None = None
    last_timestamp: datetime | None = None

class SensorSeries(Contract):
    unit: str = Field(min_length=1, max_length=40)
    coverage: Coverage
    readings: list[Reading] = Field(max_length=100000)

class Meal(Contract):
    timestamp: datetime
    carbohydrates_g: FiniteFloat | None = Field(default=None, ge=0)

class DashboardRequest(Contract):
    contract_version: Literal['1'] = '1'
    patient_id: str = Field(min_length=1, max_length=100)
    session_id: str = Field(min_length=1, max_length=100)
    interval: int = Field(ge=1, le=12)
    active_elapsed_ms: int = Field(ge=300000, le=3600000)
    source_window: SourceWindow
    sensors: dict[Sensor, SensorSeries]
    meals: list[Meal] = Field(default_factory=list, max_length=1000)
    features: dict[str, FiniteFloat] = Field(default_factory=dict)
    # Compatibility averages are explicitly excluded from physiological inference.
    cumulative_replay_averages: dict[str, FiniteFloat] = Field(default_factory=dict)

    @model_validator(mode='after')
    def validate_series(self):
        w = self.source_window
        if self.active_elapsed_ms != self.interval * 300000:
            raise ValueError('Elapsed time must match the completed five-minute interval')
        if abs((w.end - w.start).total_seconds() * 1000 - self.active_elapsed_ms) > 1:
            raise ValueError('Source window must cover the elapsed session, without looping')
        for sensor, series in self.sensors.items():
            readings = series.readings
            times = [r.timestamp for r in readings]
            if any((t.tzinfo is None) != (w.start.tzinfo is None) for t in times):
                raise ValueError('Sensor timestamp basis must match the source window')
            if any(t < w.start or t >= w.end for t in times) or any(a >= b for a, b in zip(times, times[1:])):
                raise ValueError('Readings must be ordered, unique, and inside the half-open source window')
            if any(len(r.values) != (3 if sensor == 'acc' else 1) for r in readings):
                raise ValueError('Sensor reading has the wrong number of axes')
            if sensor in ('hr', 'ibi', 'glucose') and any(r.values[0] <= 0 for r in readings):
                raise ValueError('HR, IBI and glucose must be positive')
            if sensor == 'eda' and any(r.values[0] < 0 for r in readings):
                raise ValueError('EDA must be nonnegative; zero is valid')
            expected = Coverage(sample_count=len(times), first_timestamp=times[0] if times else None, last_timestamp=times[-1] if times else None)
            if series.coverage != expected:
                raise ValueError('Coverage does not match the supplied source readings')
        for meal in self.meals:
            if (meal.timestamp.tzinfo is None) != (w.start.tzinfo is None) or not w.start <= meal.timestamp < w.end:
                raise ValueError('Meal timestamp must be inside the source window')
        return self

class ReferenceRange(Contract):
    label: str = Field(min_length=1, max_length=160)
    unit: str = Field(min_length=1, max_length=40)
    lower: FiniteFloat | None = None
    upper: FiniteFloat | None = None
    # Highlight only a range supplied by validated model configuration.
    restorative_bar_start: FiniteFloat | None = Field(default=None, ge=0, le=1)
    restorative_bar_end: FiniteFloat | None = Field(default=None, ge=0, le=1)

    @model_validator(mode='after')
    def valid_range(self):
        if self.lower is not None and self.upper is not None and self.lower > self.upper:
            raise ValueError('Reference bounds are reversed')
        a, b = self.restorative_bar_start, self.restorative_bar_end
        if (a is None) != (b is None) or (a is not None and a > b):
            raise ValueError('Restorative positions must be an ordered pair')
        return self

class MetricResult(Contract):
    availability: Literal['available', 'unavailable']
    value: FiniteFloat | None = None
    unit: str | None = None
    risk_category: Category | None = None
    bar_position: FiniteFloat | None = Field(default=None, ge=0, le=1)
    reference_range: ReferenceRange | None = None
    threshold_version: str | None = None
    confidence: FiniteFloat | None = Field(default=None, ge=0, le=1)
    risk_probability: FiniteFloat | None = Field(default=None, ge=0, le=1)
    model_version: str | None = None
    window_id: str | None = None
    explanation: str | None = Field(default=None, max_length=1500)
    reason: str | None = Field(default=None, max_length=250)
    supports_postmeal_spikes: bool = False

    @model_validator(mode='after')
    def coherent(self):
        if self.availability == 'available':
            if any(x is None for x in (self.value, self.unit, self.risk_category, self.bar_position, self.reference_range, self.threshold_version, self.model_version, self.window_id)):
                raise ValueError('Available results require numeric output and validated provenance')
            if not self.unit or not self.threshold_version or not self.model_version or not self.window_id or self.reference_range.unit != self.unit or self.reason is not None:
                raise ValueError('Available result metadata is inconsistent')
        elif any(x is not None for x in (self.value, self.risk_category, self.bar_position, self.confidence, self.risk_probability, self.explanation)) or self.supports_postmeal_spikes:
            raise ValueError('Unavailable results must not carry inferred values')
        return self

    @classmethod
    def unavailable(cls, reason: str, window_id: str):
        return cls(availability='unavailable', reason=reason, window_id=window_id)

class DashboardResponse(Contract):
    contract_version: Literal['1'] = '1'
    patient_id: str
    session_id: str
    interval: int
    window_id: str
    results: dict[str, MetricResult]

    @model_validator(mode='after')
    def complete(self):
        if set(self.results) != set(METRICS):
            raise ValueError('Response must contain each dashboard metric')
        if any(r.window_id != self.window_id for r in self.results.values()):
            raise ValueError('Result window does not match the response')
        return self
