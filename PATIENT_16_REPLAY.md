# Patient 16 hourly replay

The dashboard badge starts one hour of Patient 16 replay, pauses it, and resumes
the same session. The existing StateFlow/ViewModel and Compose layout are retained.
The current heart-rate card displays the replay's running hourly mean. Existing
historical charts, manual logging, authentication, profile, and settings remain available.
The replay does not read hardware, generate random health values, interpolate values,
or use the chart's demonstration history as input to predictions.

## CSV sources and filtering

The build packages these four original repository files as Android assets, without
maintaining duplicate CSV copies:

| Source in `local-database/patient-16-data/` | Reading | Valid records |
| --- | --- | ---: |
| `HR_016.csv` | `hr` -> heartbeat | 499 |
| `Dexcom_016.csv` | `Glucose Value`, only `Event Type == EGV` -> glucose | 488 |
| `IBI_016.csv` | `ibi` -> interbeat interval | 499 |
| `ACC_016.csv` | `acc_x`, `acc_y`, `acc_z`, retained together | 499 |

`CsvPatient16DataSource` locates columns by header name, handles CRLF, quoted commas,
escaped quotes, and empty fields, and retains valid rows in their original order.
Numeric values must be finite; heart rate, glucose, and IBI must also be positive.
ACC accepts signed values and zero, and requires all three axes to be valid.
Dexcom metadata, alerts (including numeric thresholds), insulin, and every other
non-EGV event are excluded before parsing glucose. Missing columns or an empty
valid modality produce an observable data error; no replacement readings are generated.
CSV parsing runs on `Dispatchers.IO`, outside Compose.

## Replay and aggregation

Each active second consumes one next valid row from each modality. Each modality
wraps independently to its first valid row when its own finite excerpt ends.
No timestamps are joined or aligned, and the replay cadence does not claim to
reproduce the original hardware sampling rates. A new hour starts from the first
valid row of each file.

`HourAccumulator` retains six arithmetic sums and the processed count: heartbeat,
glucose, IBI, ACC X, ACC Y, and ACC Z. Each average is its corresponding sum divided
by its count. At 5, 10, ..., 60 active minutes, `Patient16ReplaySession` takes an
immutable snapshot of all readings accumulated since the start of the hour.
**Sending a five-minute snapshot never resets the hour accumulator or replay positions.**
The first snapshot contains 300 readings per modality, the second contains 600,
and the final full-hour snapshot contains 3,600. A new session creates a fresh
accumulator and resets the session's send status.

## Timing, lifecycle, and network failures

One `replayJob` in `VitalsViewModel.viewModelScope` drives the session using coroutine
delay and a monotonic active clock (`SystemClock.uptimeMillis`). No request is sent
on start. Five-minute deadlines are relative to active session time, and all 12
deadlines include an exact aggregate snapshot even if a coroutine wake-up is late.
The final request is initiated at 60 active minutes, the session becomes completed,
and the replay job exits. Network completion status can arrive afterward.

Tapping pause checkpoints any partial active second, freezes both timers, keeps
the session object, sums, and row positions, and suspends the job on StateFlow.
Resume continues that same hour. Activity resume/pause events also gate the clock,
so background and paused wall-clock time do not contribute to either timer.
Configuration changes keep the ViewModel session; process death does not persist
an unfinished session to disk.

OkHttp performs requests asynchronously, allowing the session to keep aggregating
while a response is pending. Each call has a 30-second timeout. Connection retries
and redirects are disabled; a failed send is recorded and the next request occurs
at the next normal deadline, without resending the failed snapshot. Pausing or
leaving the foreground cancels in-flight calls without retrying them; a request
already received by the server cannot be undone. Clearing the ViewModel also
cancels calls. Responses from an older session cannot overwrite a new session's status.

`DashboardUiState.replay` exposes inactive/running/paused/completed phases, loading
and foreground state, active elapsed time, remaining hour time, next-send countdown,
attempted/completed/successful request counts, last successful send time, latest API
error, data-load error, and running averages. The badge and Toast messages identify
Patient 16 replay and hourly averaging. The dashboard shows timers and send errors.

## Request format and configuration

The existing `Api/main.py` endpoint is unchanged:

```text
GET /make_prediction?heartbeat=<mean, 2 decimals>&glucose=<mean, 2 decimals>&Interbeat_interval=<mean, 4 decimals>&ACC=x=<mean X, 2 decimals>,y=<mean Y, 2 decimals>,z=<mean Z, 2 decimals>
```

`PredictionPayload` uses `Locale.US` decimal formatting and OkHttp's
`addQueryParameter` encodes each parameter. For the current repository files, the
first five-minute request is:

```text
/make_prediction?heartbeat=82.06&glucose=107.93&Interbeat_interval=0.7810&ACC=x%3D-38.80%2Cy%3D-5.77%2Cz%3D7.92
```

These values are calculated, not hardcoded. The final hour's rounded values are
heartbeat `76.90`, glucose `106.45`, IBI `0.7869`, and ACC
`x=-38.16,y=0.01,z=8.18`.

The base URL is configured in `UI/app/build.gradle.kts` with the `fastApiBaseUrl`
Gradle property, which generates `BuildConfig.FASTAPI_BASE_URL`. Its default is
`http://10.0.2.2:8000/`, the Android emulator's route to the host's FastAPI server.
Override it for a physical device or deployed server:

```sh
bash ./gradlew :UI:app:assembleDebug -PfastApiBaseUrl=https://your-api.example/
```

The manifest grants Internet access and permits HTTP for the local development
endpoint. No credentials or secrets are added.

## Files changed

Modified:

- `UI/app/build.gradle.kts`
- `UI/app/src/main/AndroidManifest.xml`
- `UI/app/src/main/java/com/example/wolfpackvitals/MainActivity.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/data/VitalsData.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/ui/VitalsViewModel.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/ui/components/CurrentHeartRateCard.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/ui/components/PulsatingStreamingBadge.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/ui/screens/DashboardScreen.kt`

Added:

- `PATIENT_16_REPLAY.md`
- `UI/app/src/main/java/com/example/wolfpackvitals/data/replay/Patient16DataSource.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/data/replay/Patient16ReplaySession.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/data/replay/HourAccumulator.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/data/network/PredictionClient.kt`
- `UI/app/src/main/java/com/example/wolfpackvitals/ui/components/Patient16ReplayStatus.kt`
- `UI/app/src/test/java/com/example/wolfpackvitals/data/replay/Patient16DataSourceTest.kt`
- `UI/app/src/test/java/com/example/wolfpackvitals/data/replay/Patient16ReplaySessionTest.kt`
- `UI/app/src/test/java/com/example/wolfpackvitals/data/network/PredictionClientTest.kt`
- `UI/app/src/test/java/com/example/wolfpackvitals/ui/VitalsViewModelReplayTest.kt`

## Verification

On October 4, 2026, the Android debug APK built and all 14 unit tests passed
(13 replay/network tests plus the existing unit test):

```sh
ANDROID_HOME=/tmp/wolfpack-vitals-sdk bash ./gradlew :UI:app:testDebugUnitTest :UI:app:assembleDebug --console=plain
```

A temporary Android SDK was installed outside the repository to run the build.
The repository's existing Windows `local.properties` SDK path was preserved;
`ANDROID_HOME` supplied the Mac build's SDK. Existing deprecation/SDK-path warnings
did not prevent compilation. Tests use virtual time to cover the full hour, all
12 snapshots against independently computed repository-data means, independent
dataset wrapping, all ACC axes, EGV filtering, partial seconds, 13-minute pause,
background suspension, rapid pause/resume without another session, new-hour restart,
continued aggregation during in-flight requests, network failure handling, exact
query names, URL encoding, and decimal formatting. HTTP tests use MockWebServer;
no requests were sent to the production prediction service.
