# Patient 16 dashboard replay

The existing start/pause control starts an hour, pauses collection, resumes the same session,
and starts a clean session after completion. Only foreground active time counts. The dashboard
keeps its cards, chart, style toggle, expanded view, range chips, and biomarker filters.
Timers, request counts, raw averages, send timestamps, API text, and replay toasts are hidden.

## Chart collection

The graph starts empty. A separate five-minute HR sum/count adds the interval average at
5, 10, …, 60 active minutes, labelled `0–5m`, `5–10m`, …, `55–60m`. A full session contains
12 readings. Chart updates do not depend on network results. A single reading draws one
point or bar; paths start at two readings. An empty chart has no resting-reference line.
All time ranges select collected readings. Style selection is shared with the expanded chart.

`HourAccumulator` continues to retain the existing cumulative hourly HR, glucose, IBI,
and three-axis ACC means. The legacy replay consumes one row per modality per active second
and loops short excerpts for compatibility with these means and chart playback. This is
replay time, not a claim of a physiological hour. These means are included as
`cumulative_replay_averages` in POST payloads and are **excluded from model inference**.

## Source windows

Gradle packages HR, Dexcom, IBI, ACC, EDA, and TEMP if present directly from
`local-database/patient-16-data`. No duplicate CSV copies are maintained. Valid EDA zeroes
are retained. Glucose includes only EGV readings; ACC keeps all three signed axes.
Invalid/nonfinite readings are excluded. Missing optional EDA/TEMP files stay missing.
Timestamped source series keep their original timestamps and units: bpm, mg/dL, seconds,
microSiemens (`uS`), raw acceleration device counts, and Celsius when a TEMP file exists.
ACC is never silently converted to g. CSVs do not document timezone offsets; their clock
basis remains `source_local_unspecified`, preventing unverified alignment from reaching inference.

Each analysis window starts at the first HR source timestamp and ends at that timestamp plus
active elapsed time. Readings are selected on this one clock in a half-open interval, never
by independent row indices. Source windows do not loop, pad, interpolate, or invent values.
IBI sequences and temporal HR/EDA relationships remain available to a deployed workflow.
Meal/carbohydrate context is retained if present. No temperature recording is currently local.

| Local sensor | Rows | Source span |
| --- | ---: | --- |
| HR | 499 | 2020-07-16 09:29:13–09:37:31 |
| EDA | 499 | 09:29:03–09:31:07.500 |
| ACC | 499 | 09:29:03–09:29:18.562500 |
| IBI | 499 | 09:30:51.629972–09:54:25.350935 |
| Glucose | 488 EGV | Different recording span; filtered by source timestamps |
| TEMP | 0 | Missing |

## API and dashboard results

`GET /make_prediction` retains its four original query parameters and string response for
compatibility. Replay and manual sync use the new `POST /make_prediction` in the same FastAPI
service. See [API contract and integration](Api/DASHBOARD_API.md).

A request identifies patient `16`, a fresh UUID session, completed interval number, source
window, per-sensor units/coverage/readings, meal context, features, and compatibility averages.
The API returns independent `hr_eda`, `glucose_variability`, `hrv`, and `prediabetes_risk` results.
The client validates each result separately and checks the response identity before binding it.

Cards start with empty bars and null confidence. A configuration readiness check immediately
shows **Unavailable** with an actionable reason when configuration/access is missing; configured
cards show **Collecting data** until a completed window is analyzed. Available model
categories drive badge, color, severity, and filter membership together. Typical/low are
**Stable**; moderate/elevated/high are **Monitoring**, with actual severity shown separately
and high colored red. Positions and reference ranges come from validated model configuration.
HRV highlights only a model-supplied restorative range. LF/HF is not treated as a universal
sympathovagal/restorative measure. Postmeal-spike language requires explicit model support.
Unsupported/failed results become **Unavailable**. A retained previous result becomes
**Outdated** and is excluded from Stable/Monitoring filters. Confidence is populated only
from validated model confidence; risk probability is a separate detail field.

Manual sync sends the last completed source window through the same analysis client.
Asynchronous OkHttp requests do not block collection. Session, completed interval, refresh
attempt, and cancellation-generation guards reject stale callbacks. Pause/background cancel
pending requests; resume continues aggregation. The client allows 150 seconds overall,
130 seconds reading, and 10 seconds connecting. Server inference defaults to 120 seconds
plus a bounded 10-second deployment verification. Actual model latency cannot be measured
until an applicable workflow exists; operators should tune both budgets together.

## Verified Databricks capabilities

The live audit on 2026-10-04 found five sensor tables and an HbA1c label table, patient IDs
1–15, no patient 16 rows, no carbohydrate events, no temperature table, no threshold tables,
no registered models/functions in `workspace.wolfpack-vitals`, no scheduled jobs/pipelines,
and only general foundation-model serving endpoints. Inspected project notebooks contained
empty stubs and a generated ACC exploration query, not a prediction/calculation workflow.
See [audit evidence](Api/databricks_verification.json) and the read-only
[verification script](Api/verify_databricks.py). The presence of Genie query access does not
verify deployment of clinical models. The API deliberately returns unavailable results
until associated models, thresholds, clock alignment, and required source coverage are verified.

## Running and verification

From the repository root:

```sh
python -m uvicorn Api.main:app --host 0.0.0.0 --port 8000
python -m unittest discover -s Api/tests -v
bash ./gradlew :UI:app:testDebugUnitTest :UI:app:assembleDebug
bash ./gradlew :UI:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.wolfpackvitals.ui.DashboardVerificationTest
```

Set `ANDROID_HOME` to an installed SDK. `-PfastApiBaseUrl=https://your-api.example/` overrides
the default emulator URL `http://10.0.2.2:8000/`. The native UI verification captures charts,
cards, and detail dialogs under the app external-files `dashboard-verification` directory.
Test predictions and thresholds are explicitly synthetic fixtures and never shipped as
clinical configuration. Unit/API tests cover collection, twelve intervals, interval vs hourly
means, pause/background/resume/restart, filters/styles, failures, partial results, out-of-order
responses, refresh guards, missing temperature, null confidence, and threshold boundaries.

Latest verification (2026-10-04): debug APK build, 26 Android unit tests, 20 API tests,
and 3 native Compose tests passed. Native captures cover moderate/high glucose severity,
model-defined HRV range highlighting, and confidence/probability separation with explicitly
synthetic fixtures. Existing APK and updated screenshots were compared; labels fit and
replay diagnostics remain hidden. No applicable live prediction model was found.

Availability update: cards now check configuration before collection and preserve reasons
through checkpoints. See [current verification and external dependencies](Api/ANALYSIS_AVAILABILITY.md)
for the effective URL, fresh audit, exact local coverage and independent refresh behavior.
