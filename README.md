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

## Quick start (Android Studio)

Developer A owns the camera app on `feat/android-camera`.

1. Install JDK 17 and Android SDK 34.
2. Open this folder in Android Studio, or from the repo root run `gradlew.bat assembleDebug`.
3. Connect a physical Android phone with USB debugging and install the debug APK.
4. Optional cloud coaching stays off until `local.properties` (not committed) contains `frameai.baseUrl` and `frameai.demoToken`.
5. If the project folder locks build files, set `frameai.buildDir` in `local.properties` to a directory outside that folder.

Face detection and `.cube` LUTs are Developer B. Until those are wired in `MainActivity`, the preview shows the frozen sample guides (labeled Sample) and Apply uses an on-device color preview, not a LUT.

## Demo journey (90s)

1. Open FrameAI → rear camera preview + rule-of-thirds grid.
2. Point at a teammate → face target + short framing tip (offline).
3. Capture → original JPEG saved (never overwritten by edit).
4. Apply Warm Film or Cool Cinema → before/after → save.
5. Optional: **Why this shot?** → Gemini coaching, or labeled offline tip if cloud is down.

## Contracts

See `contracts/` for LiveGuide and ShotReview JSON schemas and mocks.

## Safety

Never commit API keys, Cloud Run tokens, or `.env` files. Cloud is optional; offline P0 must work in airplane mode.
