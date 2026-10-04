# Dashboard analysis availability — 2026-10-04

Implemented configuration readiness before collection, independent inference calls for eligible
metrics, and regression coverage for retained Outdated results. Clinical availability remains
blocked by the external workflow and source dependencies below.

## API connection and exact responses

The generated debug BuildConfig uses `http://10.0.2.2:8000/`; the Gradle default agrees and
can be overridden with `-PfastApiBaseUrl=...`. No device was attached to adb, so an installed
APK's URL and on-device connectivity could not be inspected. No backend was listening on
8000 at the beginning of this task. A local uvicorn process was started for HTTP verification
with `Api.main:app`, binding `0.0.0.0:8000`; host requests used `127.0.0.1:8000`.
`Api.main` loads `Api/.env`; `DASHBOARD_MODEL_CONFIG` is unset in that effective environment.
Legacy Gemini/SDK imports are now lazy so optional legacy dependencies cannot prevent
structured analysis or readiness from starting.

`GET /analysis_readiness` and real CSV POST requests at intervals 1, 2 and 12 return this
reason independently for **all four metrics**:

> Analysis is not configured. Ask the service operator to install a verified workflow.

See [HTTP and recording evidence](dashboard_readiness_verification.json). The original
response was “No verified dashboard prediction workflow is configured.” Configuration
failure is the first blocker; timestamp/coverage failures are not currently reached by
inference. With a suitable configuration, unverified source timestamps remain blocked.

## Behavior and workflow contract

The ViewModel checks readiness when created and on replay restart. Manual sync before the
first window retries readiness. Each card shows its own actionable reason immediately;
checkpoint collection cannot reset known configuration failures to Collecting data. Readiness
network failures show a connection/retry reason. The HTTP preflight has a 10-second budget,
does not invoke a model, and does not increment analysis transmission counts. Late preflight
responses cannot overwrite a newer session or analysis attempt.

Configuration readiness reports `configured`, `reason`, `model_version`, and the full
versioned policy as `requirements` for each metric. `configured=true` only confirms local
configuration and service credentials; source validation and live deployment/version checks
still run for every analysis. No readiness response is a prediction.

Eligible metrics invoke the verified custom workflow concurrently and independently. Each
invocation receives `inputs[0].requested_metrics` containing just that metric, and only that
policy's required sensor series. The deployed workflow must honor this selector and calculate
supported metrics without demanding unavailable sibling inputs. Responses still use the
existing `predictions[0].results` contract. Compatibility replay averages never enter inference.
A failed service call, malformed metric, missing temperature or unsupported risk does not
clear another metric's valid result. The UI retains previous numeric values/provenance as
Outdated when a new window, unavailable refresh, network failure, or cancellation occurs.
A successful supported refresh restores Stable/Monitoring independently.

## Missing workflow dependency

The [fresh live audit](databricks_readiness_audit.json) found no registered models or functions
in `workspace.wolfpack-vitals`, no accessible jobs/pipelines, only foundation-model serving
endpoints, and no patient 16 rows. The earlier notebook review in
[the prior audit](databricks_verification.json) found stubs/exploration rather than a calculation
or training workflow. These audit scopes do not establish absence in inaccessible workspaces.

No suitable verified workflow was available to connect, so `DASHBOARD_MODEL_CONFIG` remains
unset. Installing a placeholder or test policy would falsely certify clinical thresholds.
The model owner must supply a custom serving endpoint and entity/version, verification evidence,
and its exported policy manifest: configuration source, metric/output units, threshold version,
contiguous threshold bands, reference ranges, required sensor units/sample counts/maximum gaps,
meal requirements and any validated confidence or postmeal interpretation support. See the
`Deployment` and `MetricPolicy` schemas and [integration instructions](DASHBOARD_API.md).
Set `DASHBOARD_MODEL_CONFIG` to that verified manifest only after those dependencies exist.

## Missing source dependencies

The provider must certify patient mapping, source timezone/offset (including DST ambiguity),
clock synchronization, device calibration and units before converting timestamps to UTC.
CSV filenames ending 016 and a Dexcom LastName field of 016 are insufficient certification;
remote IDs 1–15 must not be relabeled as patient 16. No offset has been assigned here.
Unit labels currently preserved by the loader are bpm, uS, device counts, seconds and mg/dL;
the missing temperature series would require verified Celsius exports. Raw acceleration
counts must not silently become g. No synchronization or unit evidence was supplied.

| Sensor | Local span on unspecified source clock | Rows in first 5 min | Rows in first 10 min |
| --- | --- | ---: | ---: |
| HR | July 16 09:29:13–09:37:31 | 300 | 499 |
| EDA | July 16 09:29:03–09:31:07.500 | 459 | 459 |
| ACC | July 16 09:29:03–09:29:18.562500 | 179 | 179 |
| IBI | July 16 09:30:51.629972–09:54:25.350935 | 92 | 170 |
| Glucose | July 16 10:43:25–July 18 03:18:22 | 0 | 0 |
| Temperature | Missing | 0 | 0 |

The full replay source window ends at July 16 10:29:13, before glucose begins. There is no
all-sensor overlap. HR/EDA and IBI excerpts do not establish complete coverage for a
five-minute or one-hour workflow. Exact adequacy cannot be certified without its verified
minimum counts/gap policy. Obtain complete patient 16 exports covering a common certified
clock window, including temperature for risk and meal context where required; use longer
recordings for every cumulative refresh through 60 minutes. No suitable additional recordings
were found in the local project or accessible patient tables. Source selection remains
half-open, cumulative, and unlooped; no coverage checks or clinical thresholds were weakened.

## Verification

22 API tests and 30 Android unit tests passed; the debug APK and native test sources compiled.
API tests cover missing/invalid configuration and credentials, readiness policy metadata,
first-window requests, subsequent supported requests, independent service failure/partial
results, unverified clocks, units, gaps/insufficient coverage, missing temperature, invalid
outputs, threshold/version checks and exclusion of replay averages. Android tests cover
pre-five-minute reasons, readiness failures, twelve scheduled requests, unavailable/network
refresh retention, independently available siblings and callback ordering/cancellation.
HTTP verification uses actual CSV readings at 5, 10 and 60 minutes; it generates no predictions.
Synthetic model outputs exist only in tests.

Run:

```sh
python3 -m unittest discover -s Api/tests -v
bash ./gradlew :UI:app:testDebugUnitTest :UI:app:assembleDebug :UI:app:compileDebugAndroidTestKotlin
python3 -m Api.verify_dashboard_readiness
```

Native instrumentation was compiled but could not be executed without a connected device.
Live inference and clinical accuracy cannot be tested until the verified workflow and adequate
certified recordings are provided.
