# Developer B handoff

Branch: `feat/vision-lut`

B owns face detection, the offline guide, LUT grading, and the optional review server. CameraX, Compose, and save/review UI came from Developer A. `MainActivity` now constructs the detector, `GuideEngine`, and `CubePhotoGrader`.

## What the camera calls

### Face

`MlKitPrimaryFaceDetector.detectPrimaryFace(imageProxy)` implements `PrimaryFaceDetector`.

- Bundled ML Kit, FAST mode, one primary face, no tracking and no identity.
- The detector **does not close** `imageProxy`. `FrameCameraController` closes it.
- The returned `RawFaceBox` is in the unrotated buffer. `AnalysisCoordinateMapper` turns that into PreviewView space.
- Null means no face. A raw face that cannot be mapped keeps the static target. Do not invent a moving box.
- Dependency: `implementation("com.google.mlkit:face-detection:16.1.7")`
- `FaceDetectionAdapter` is the older preview-space helper. Do not register it on this camera controller, because it closes the frame.

The JVM tests compile `app/src/main/java` and skip Android-only files: `MainActivity`, `ui/`, `camera/`, `ContractJson`, `FaceDetectionAdapter`, `MlKitPrimaryFaceDetector`, `BitmapLutRepository`, and `CubePhotoGrader`. Do not also package the `brain` jar into the app.

### Guide

`GuideEngine.suggest(faceBox, steady)` returns exactly one `LiveGuide`. `kind` and `source` are the contract strings.

- Target `(0.67, 0.33)`. READY when the face center is within ±0.08 x and ±0.10 y. Otherwise ALIGN. Null is NO_FACE.
- Messages match the frozen mocks. `source` is `offline_rules`.
- Hints do not say "move left" or "move right".
- `steady` is accepted and does not add a second tip.
- `GuideSmoother` eases the box and emits about 4 times per second. A face appearing or disappearing is delivered immediately. A lost face is `NO_FACE` even inside the throttle window, so a previous READY cannot linger.
- `MainActivity` still calls `GuideEngine` directly. A4 should wrap that call with `GuideSmoother` once the preview transform is trusted. B does not edit the camera screen for this.

### A4 geometry and copy

These constants in `GuideEngine` are the source of truth. Do not draw a fixed 42dp ring and call it the ready area.

| | Value |
|---|---|
| Target | `(0.67, 0.33)` in PreviewView space, origin top-left |
| Ready when | face center is within `±0.08` x and `±0.10` y of the target, inclusive |
| Otherwise, with a face | `ALIGN_SUBJECT` |
| No face | `NO_FACE`, subject coordinates null |

Draw the target as an ellipse of half-width `0.08` and half-height `0.10` of the preview, in the same normalized space. The dot is the face center. Do not add "move left" or "move right".

Copy, already matched to the frozen mocks:

- ALIGN: `Align the face with the target circle`
- READY: `Framing looks good — hold steady and shoot`
- NO_FACE: `Use the grid to place your subject`

`source` stays `offline_rules`. `kind` stays one of the three contract strings.

### LUT

`LutPack.looks` is only Warm Film and Cool Cinema. The other `.cube` files stay in `app/src/main/assets/luts/` and are not loaded or offered. Several are camera-log conversions and must not be applied to a normal phone JPEG.

- `warm_film` is the downloaded `cinematic_04.cube`
- `cool_cinema` is the downloaded `cinematic_08.cube`
- `identity_4.cube` is for the identity check and is not a user preset
- Review strength is `LutPack.REVIEW_INTENSITY` (`0.85`). The repository default of `0.7` is not what the screen uses.

`CubePhotoGrader` calls `BitmapLutRepository.fromAssets(assets).apply(bitmap, lutId, intensity)`. The LUT is resolved before the bitmap is read. Unknown ids throw `UnknownLutException`. Malformed cubes throw `CubeParseException`. Neither failure is turned into a copy of the original.

- Returns a new bitmap. Does not modify or recycle the original.
- Long edge above 1024 px is graded at 1024. Call this off the main thread.
- Warm Film and Cool Cinema are the SDR looks for a normal JPEG. This is a still-photo grade, not a live GPU preview.
- At strength 0.85, neutral gray stays off the 0 and 255 rails, warm raises red over blue, and cool raises blue over red. Dark values stay dark and bright values stay bright.

### Offline coaching

`OfflineCoach.review(guide)` when Analyze cannot reach the server.

`source` is `offline_rules`. The summary is prefixed `Offline tip`. Never label that response as Gemini.

## Coordinate space

The guide target is on the **displayed** preview, origin top-left, both axes 0..1.

1. ML Kit returns an upright box when `InputImage.fromMediaImage(image, rotation)` is used. `MlKitPrimaryFaceDetector` converts that box back into the unrotated buffer before returning `RawFaceBox`.
2. `AnalysisCoordinateMapper` maps the buffer box into the `PreviewView` with CameraX `ImageProxyTransformFactory`. A failed mapping with a raw face keeps the static target.

If the on-phone marker is obviously offset, stop drawing it and keep the static target plus the NO_FACE / ALIGN copy. Say that tracking is an MVP limit. Do not present a wrong box as accurate.

## Tests

From `brain/`, with JDK 21 or newer:

```
gradlew.bat test
```

That covers NO_FACE / READY / ALIGN, identity LUT error within 1/255, bad `.cube` files, warm vs cool on the sample photos, original pixels unchanged, and the preview crop math.

From `server/`:

```
py -3 -m pytest
```

## Optional server

`GET /health` → `{"status":"ok","aiMode":"mock","geminiConfigured":false}`

`POST /v1/shot-review` and `POST /v1/live-coach` take a multipart field `image` plus header `X-Demo-Token`.

- `AI_MODE=mock` returns the frozen mock JSON with `source=demo_mock`.
- `AI_MODE=gemini` calls Gemini when `GEMINI_API_KEY` is set, or when Application Default Credentials and `GOOGLE_CLOUD_PROJECT` are set. One request at a time. Timeout is about 12 seconds. The default model is `gemini-3.5-flash-lite` in location `global`.
- Missing credentials or any other mode returns 503. The app should show the offline coach, not a fake Gemini success.
- 401 bad token, 400 not a JPEG, 413 over 2 MB, 429 already busy, 502 provider failure. The response body does not include the key, the image, or the raw provider error.

Do not put `GEMINI_API_KEY`, `DEMO_TOKEN`, or a service-account JSON in the app or in git. Cloud Run deploy stays manual until the offline camera path is already working:

```
gcloud run deploy frameai-coach --source . --region us-central1 \
  --max-instances 1 --min-instances 0 --cpu 1 --memory 512Mi \
  --set-env-vars AI_MODE=gemini,MODEL_ID=gemini-3.5-flash-lite,GOOGLE_CLOUD_LOCATION=global \
  --set-secrets GEMINI_API_KEY=frameai-gemini-key:latest,DEMO_TOKEN=frameai-demo-token:latest
```

Confirm the project, billing, and whether unauthenticated access is actually required before answering the deploy prompts.
