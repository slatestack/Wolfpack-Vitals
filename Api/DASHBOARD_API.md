# Structured dashboard API, version 1

Run the existing service as `uvicorn Api.main:app` from the repository root, or
`uvicorn main:app` from `Api`. Install `requirements.txt`. `/docs` exposes the full
request/response schema defined in [contracts.py](contracts.py).

`GET /make_prediction` remains compatible. `POST /make_prediction` accepts a JSON
`DashboardRequest`; FastAPI runs inference in its worker pool. The Android dashboard
never uses legacy Gemini text for numerical output.

## Request

- `contract_version`: `"1"`.
- `patient_id`, `session_id`: source patient identity and session UUID.
- `interval`: completed five-minute interval, 1–12.
- `active_elapsed_ms`: interval × 300,000.
- `source_window`: `id`, `start`, `end`, `time_basis`. The window is cumulative from
  session source start, half-open `[start, end)`. `verified_utc` requires explicit UTC
  offsets; `source_local_unspecified` preserves local timestamps without inventing an offset.
- `sensors`: maps `hr`, `eda`, `acc`, `temp`, `ibi`, `glucose` to `unit`, `coverage`,
  `readings`. Coverage contains `sample_count`, `first_timestamp`, `last_timestamp`;
  readings contain original `timestamp` and `values` (one value, or three ACC axes).
- `meals`: timestamped `carbohydrates_g`, nullable; empty when unavailable.
- `features`: finite named features, optionally computed by a verified upstream workflow.
- `cumulative_replay_averages`: compatibility means, **not inference features**.

The API rejects unordered, duplicate, out-of-window, nonfinite, wrong-axis, and
coverage-inconsistent data with HTTP 422. EDA zero is valid. Missing sensors can be
omitted or contain zero readings; model requirements decide availability per metric.

## Response

Echoes patient/session/interval/window identity and a `results` object containing
`hr_eda`, `glucose_variability`, `hrv`, and `prediabetes_risk` independently.

Each result has `availability` (`available`/`unavailable`), `value`, `unit`,
`risk_category` (`typical`/`low`/`moderate`/`elevated`/`high`), `bar_position` (0–1),
`reference_range`, `threshold_version`, optional `confidence`, `risk_probability`,
`model_version`, `window_id`, `explanation`, `reason`, `supports_postmeal_spikes`.
Missing numerical values and confidence are JSON null. Unsupported results are normal
HTTP 200 partial outcomes, not invented numbers or raw service errors.

A reference range contains `label`, `unit`, optional `lower`/`upper`, and optional
`restorative_bar_start`/`restorative_bar_end`. Restorative positions are an ordered 0–1
pair supplied by model configuration, not universal LF/HF thresholds. Probability and
confidence are separate quantities. Confidence must have validation evidence in the
associated model configuration before a nonnull output is accepted.

## Databricks integration

[dashboard_analysis.py](dashboard_analysis.py) integrates a version-pinned **custom**
Databricks serving workflow returning the dashboard contract. It does not turn Genie
or foundation-model prose into clinical numbers. The current workspace has no such
workflow; [databricks_verification.json](databricks_verification.json) records the audit.
The adapter is implemented and tested against synthetic serving responses, not certified
against a deployed clinical model.

Before enabling results, verify synchronized source clocks, required recording lengths,
patient mapping, temperature/meal sources, and an appropriate model/calculation workflow.
Export the associated threshold/input configuration into a deployment manifest, then set
`DASHBOARD_MODEL_CONFIG` to its path. Do not use test fixture thresholds.

The manifest follows the `Deployment` schema in `dashboard_analysis.py`:

- `endpoint`, `entity_name`, `entity_version`: one ready serving entity/version.
- `configuration_source`: provenance of the associated model configuration or threshold table.
- `metrics`: each supported metric maps to a `MetricPolicy` containing output `unit`,
  `threshold_version`, `reference_range`, contiguous `bands`, and `required_sensors`.
- Each band has inclusive `lower`, exclusive `upper`, `category`, and `bar_position`.
  Null end bounds cover unbounded output. Bounds must cover outputs without gaps or overlaps.
- Each sensor requirement contains `unit`, `min_samples`, `max_gap_seconds`, including
  allowed leading/trailing gaps. These come from the actual model, not UI assumptions.
- Optional policy flags: `requires_meals`, `confidence_validated`,
  `supports_postmeal_spikes`, all false by default.

Structural minimums require HR+EDA for heart/skin patterns, glucose for variability,
IBI time series for HRV, and all six modalities for the complete prediabetes workflow.
Input-specific unavailable outcomes do not block other supported metrics. Unverified
clock basis, absent sensors, gaps, unknown units, missing required meals, or insufficient
recording length prevent that metric from invoking inference.

The adapter verifies the live deployment is ready, serves exactly the configured entity
and version, and is not a foundation model. It sends `{"inputs": [request]}` to the serving
endpoint, excluding compatibility averages. The workflow must return
`{"predictions": [{"results": {metric: MetricResult, ...}}]}`. Every available output must
match the model/threshold versions, units, reference range, category threshold band,
normalized position, and requested window. Invalid siblings become unavailable
independently. Unsupported metrics remain unavailable. Model explanations must be grounded
in the returned prediction; the UI shows no legacy Gemini response or raw API body.

Credentials remain server-only in environment variables or untracked `Api/.env`:
`DATABRICKS_HOST`, `DATABRICKS_TOKEN`. Optional
`DASHBOARD_INFERENCE_TIMEOUT_SECONDS` defaults to 120. Android's total budget is 150
seconds. No model latency claim is made until real inference is measured.

For a fresh read-only audit: `python -m Api.verify_databricks`. The script lists accessible
metadata and executes only aggregate SELECT queries. It may resume the configured SQL
warehouse. `DATABRICKS_WAREHOUSE_ID` chooses a warehouse; otherwise a serverless warehouse
is selected. Output excludes secrets and individual patient readings.

Genie supports queries and SQL-result attachments; this does not establish that a
prediction model exists. [Databricks Genie API](https://docs.databricks.com/api/genie/v1/conversation).
The UI also removes the universal LF/HF restorative/balance claim.
[Billman, 2013](https://www.frontiersin.org/journals/physiology/articles/10.3389/fphys.2013.00026/full).
