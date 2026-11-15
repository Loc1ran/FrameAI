# FrameAI

HackUTA 2026 — Android camera that helps beginners make better cinematic photographs.

**Current plan:** `docs/three-developer-gemini-coach-plan.md`. The camera coaches any subject. The preview shows a dashed goal and a solid current frame, not a paragraph. Gemini, when called, reads the current frame.

## Stack

- Kotlin / Jetpack Compose / CameraX
- ML Kit face detection (on-device)
- Bundled SDR `.cube` LUTs: `warm_film`, `cool_cinema`
- Optional: Cloud Run + Gemini (`POST /v1/shot-review`)

Package: `com.team.frameai` · minSdk 26

## Repo layout

| Path | Owner |
|------|--------|
| `app/camera`, `app/ui`, Gradle/Manifest | Developer A, installed by C on the phone |
| `app/vision`, `app/guide`, `app/lut`, assets | Developer B |
| `contracts/` | Frozen jointly — do not invent field names |
| `server/` | Developer C, on this PC |
| `docs/three-developer-gemini-coach-plan.md` | Current plan |

## LUT catalog

Gemini ranks the 10 best phone-photo looks for the current picture. Review shows that row and scrolls sideways. Apply one look at a time, and save writes the look on screen. `none` leaves the color unchanged. Review strength is 0.85.

## Quick start (Android Studio)

1. Install JDK 21 and Android SDK 34. The app build uses JDK 21 because the Android Studio runtime is too new for this Gradle version.
2. Open this folder in Android Studio, or from the repo root run `gradlew.bat assembleDebug`.
3. Connect a physical Android phone with USB debugging and install the debug APK.
4. The phone calls this PC over USB with `adb reverse tcp:8080 tcp:8080`. It does not need the same Wi-Fi, and it does not call Google directly.
5. If the project folder locks build files, set `frameai.buildDir` in `local.properties` to a directory outside that folder.

`MainActivity` uses ML Kit face detection, `GuideEngine`, and the bundled `.cube` grades. Warm Film and Cool Cinema are the review chips. The original JPEG is not overwritten.

## Demo journey (90s)

1. Open FrameAI. The rear preview shows the rule-of-thirds grid.
2. Point at a person, a place, or an object. A dashed frame is the goal, a solid frame is the current view, and an arrow connects them.
3. When they match, the mark turns green and a check appears.
4. Capture. The original JPEG is kept.
5. Apply Warm Film or Cool Cinema as a separate edit.

## Contracts

See `contracts/` for LiveGuide and ShotReview JSON schemas and mocks.

## Developer B modules

Face, guide, and LUT logic live in `app/src/main/java/com/team/frameai/` and are unit-tested from `brain/` without a phone:

```
brain\gradlew.bat test
```

Developer A compiles those sources with the app. `MlKitPrimaryFaceDetector` does not close the `ImageProxy`; the camera controller does.

Shared AI server, from `server/`. Other developers call this. Credentials stay on the machine that runs the server and are not committed.

```
copy ..\.env.example .env
```

Google Cloud login, when an API key is not available:

```
powershell -c "iex (irm https://storage.googleapis.com/cloud-samples-data/adc/setup_adc.ps1)"
```

Put the project ID in `GOOGLE_CLOUD_PROJECT`, set `AI_MODE=gemini`, and leave `GEMINI_API_KEY` empty. An API key in `GEMINI_API_KEY` still works and does not need that login. Then:

```
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8080
```

`GET /health` shows `geminiConfigured` without revealing the key. `POST /v1/live-coach` and `POST /v1/shot-review` take a JPEG field named `image` and the header `X-Demo-Token`. `AI_MODE=mock` needs no key and returns the frozen demo JSON.

The current plan is `docs/three-developer-gemini-coach-plan.md`. Older plans in `docs/` are retired.

## Safety

Never commit API keys, Cloud Run tokens, or `.env` files. Cloud is optional; offline P0 must work in airplane mode.
