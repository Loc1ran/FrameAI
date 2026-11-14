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

1. Open this folder in Android Studio (Gradle project to be bootstrapped in H+0).
2. Connect a physical Android phone with USB debugging.
3. Build/install debug APK and confirm camera permission + preview.

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

Developer A compiles those sources with the app. Add `com.google.mlkit:face-detection:16.1.7` before the live face adapter will build. The adapter closes each `ImageProxy`; do not close it again.

Optional review server, mock mode, from `server/`:

```
py -3 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
$env:AI_MODE = "mock"
$env:DEMO_TOKEN = "localtest"
uvicorn main:app --host 127.0.0.1 --port 8080
```

Handoff details: `docs/developer-b-handoff.md`.

## Safety

Never commit API keys, Cloud Run tokens, or `.env` files. Cloud is optional; offline P0 must work in airplane mode.
