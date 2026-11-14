# FrameAI

HackUTA 2026 — Android camera that helps beginners make better cinematic photographs.

**Offline MVP first:** live framing guidance → real capture → LUT grading. Optional Gemini coaching after the user asks.

## Stack

- Kotlin / Jetpack Compose / CameraX
- ML Kit face detection (on-device)
- Bundled SDR `.cube` LUTs: `warm_film`, `cool_cinema`
- Optional: Cloud Run + Gemini (`POST /v1/shot-review`)

Package: `com.team.frameai` · minSdk 26

## Repo layout

| Path | Owner |
|------|--------|
| `app/camera`, `app/ui`, Gradle/Manifest | Developer A |
| `app/vision`, `app/guide`, `app/lut`, assets | Developer B |
| `contracts/` | Frozen jointly — do not invent field names |
| `server/` | Optional cloud (B or C) |
| `docs/` | Playbook + notes |

## Frozen LUT IDs

- `warm_film`
- `cool_cinema`

The review screen offers only those two looks. `warm_film` is the packaged `cinematic_04` file and `cool_cinema` is `cinematic_08`, applied at strength 0.85. Other files in the asset folder are not selectable. Shot review may suggest only these two ids, or `none`.

## Quick start (Android Studio)

Developer A owns the camera app on `feat/android-camera`.

1. Install JDK 17 and Android SDK 34.
2. Open this folder in Android Studio, or from the repo root run `gradlew.bat assembleDebug`.
3. Connect a physical Android phone with USB debugging and install the debug APK.
4. Optional cloud coaching stays off until `local.properties` (not committed) contains `frameai.baseUrl` and `frameai.demoToken`.
5. If the project folder locks build files, set `frameai.buildDir` in `local.properties` to a directory outside that folder.

`MainActivity` uses ML Kit face detection, `GuideEngine`, and the bundled `.cube` grades. Warm Film and Cool Cinema are the review chips. The original JPEG is not overwritten.

## Demo journey (90s)

1. Open FrameAI → rear camera preview + rule-of-thirds grid.
2. Point at a teammate → face target + short framing tip (offline).
3. Capture → original JPEG saved (never overwritten by edit).
4. Apply Warm Film or Cool Cinema → before/after → save.
5. Optional: **Why this shot?** → Gemini coaching, or labeled offline tip if cloud is down.

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

Handoff details: `docs/developer-b-handoff.md`.

## Safety

Never commit API keys, Cloud Run tokens, or `.env` files. Cloud is optional; offline P0 must work in airplane mode.
