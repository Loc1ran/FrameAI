# FrameAI

HackUTA 2026 — Android camera that helps beginners frame a shot, then grade it with a film look.

The camera opens in **Subject** mode. A dashed frame is the goal and a solid frame is the subject. When they match, the mark turns green. **Scene** asks the optional coach to read the activity and its surroundings. **Action** takes eight frames and keeps one. The original JPEG is kept. A look is a separate edit.

## Stack

- Kotlin / Jetpack Compose / CameraX
- ML Kit face and object detection, on device
- Bundled `.cube` LUTs. Phone-photo grades are the ones offered in review. Camera-log conversions stay in the pack and are left out of that row.
- Optional local server: FastAPI + Gemini

Package: `com.team.frameai` · minSdk 26 · compileSdk 34

AI is off until you turn it on. The phone does not call Google. It calls this PC at `http://127.0.0.1:8080` through `adb reverse`. Subject framing still works with the server stopped.

## Modes

| Mode | What you get |
|------|----------------|
| Subject | Follow a person or object. Tap the subject to lock it; tap empty space to let detection choose. The guide names one move. An optional lighting map is in the ••• menu. |
| Scene | One note about the action and its surroundings. Turn AI on first. A missing or mock reply is not marked ready. |
| Action | One shutter press meters, then saves eight JPEGs with a fast shutter. AI on, Gemini picks a usable frame. AI off, the phone ranks overall detail on device. A failed check keeps the originals in app storage. |

Photo shape defaults to 4:3. Full, 16:9, and 1:1 are in the toolbar. The preview stays portrait; labels rotate with the phone. Review can turn sideways.

The style row (Auto, Cinematic, Portrait, Street, Landscape) is sent with the live coach. It does not move the lens. Zoom − and Zoom + do.

## Repo layout

| Path | Contents |
|------|----------|
| `app/` | Camera, guide, review, and the debug APK |
| `brain/` | JVM tests for guide, vision, and LUT code. The app does not package this module. |
| `contracts/` | Schemas and mocks. Do not invent field names. |
| `server/` | Coach API. Credentials stay in `server/.env` and are not committed. |
| `lut_assets/` | Scripts that write the LUT catalog |
| `docs/` | Feature notes. `docs/three-developer-gemini-coach-plan.md` is the original split. |

## Looks

Review shows up to 10 phone-photo looks and scrolls sideways. Apply one at a time. Save writes the look on screen to `Pictures/FrameAI`. `none` leaves the color unchanged. Review strength is 0.85. The full-size original stays; an edited copy is at most 1024 px on the long edge.

If `frameai.baseUrl` and `frameai.demoToken` are set, opening review sends the photo once to rank those 10 looks. `Get photo tip` is separate: turn on **Ask cloud coach**, then tap the button. The card is what worked, what could improve, and one principle. With the switch off, the tip stays on device.

## Quick start (Android Studio)

1. Install JDK 21 and Android SDK 34. Brain tests target 21. The app bytecode target is 17.
2. Open this folder in Android Studio, or from the repo root run `gradlew.bat assembleDebug`.
3. In `local.properties`, set the phone’s coach address and token. Add `frameai.buildDir` only if the project folder locks build outputs:

```
frameai.baseUrl=http://127.0.0.1:8080
frameai.demoToken=localtest
```

4. Install the debug APK on a phone with USB debugging.
5. Forward the coach port. The phone does not need the same Wi-Fi:

```
adb reverse tcp:8080 tcp:8080
```

6. On the camera, open ••• and turn AI on. That shares preview frames while it stays on.

`MlKitPrimaryFaceDetector` does not close the `ImageProxy`. The camera controller does.

## Coach server

From `server/`:

```
copy ..\.env.example .env
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8080
```

`AI_MODE=mock` needs no key and returns the frozen demo JSON. For Gemini, put the project id in `GOOGLE_CLOUD_PROJECT`, set `AI_MODE=gemini`, and leave `GEMINI_API_KEY` empty after this login:

```
powershell -c "iex (irm https://storage.googleapis.com/cloud-samples-data/adc/setup_adc.ps1)"
```

An API key in `GEMINI_API_KEY` also works and does not need that login. `GET /health` reports `geminiConfigured` without showing the key. Default model is `gemini-3.5-flash-lite`.

Every POST takes the header `X-Demo-Token` and a JPEG. Images over 2 MB are rejected.

| Endpoint | Body |
|----------|------|
| `POST /v1/live-coach?composition=subject` | Current frame as `image`, plus form fields `style` and optional `instruction`. Scene uses `composition=scene` and expects the response header `X-FrameAI-Composition: scene`. |
| `POST /v1/shot-review` | One JPEG field named `image`. |
| `POST /v1/action-best-shot` | Two to eight JPEG fields named `images`. |

A cloud answer is `source=gemini`. A mock answer is `source=demo_mock` and is not treated as a real scene analysis or a real best-shot pick. On-device subject coaching is `source=offline_rules`.

## Tests

```
brain\gradlew.bat test
gradlew.bat test
server\.venv\Scripts\python.exe -m pytest
```

Run the server tests from `server/`. The app build checks the packaged mocks in `app/src/main/assets/contracts/` against `contracts/` before compile.

## Demo (about 90 seconds)

1. Open FrameAI. Subject mode, 4:3, guide on.
2. Point at a person or an object. Tap it if the tracker picks the wrong thing.
3. Follow the move cue until the frame turns green, then capture.
4. Apply one look and save. The original file is still there.
5. With the server up and AI on, switch to Scene for one surrounding note, or Action for an eight-frame pick.

## Safety

Never commit API keys, Cloud credentials, or `.env` files. Cloud is optional. Subject mode and a local Action ranking work with AI off.
