# Developer B handoff

Branch: `feat/vision-lut`

B owns face detection, the offline guide, LUT grading, and the optional review server. CameraX, Compose, save/review UI, and the release branch stay with Developer A. These files do not edit `MainActivity`.

## What A calls

### Face

`FaceDetectionAdapter.analyze(imageProxy, viewWidth, viewHeight, mirrorX = false, onResult)`

- Bundled ML Kit, FAST mode, one primary face, no tracking and no identity.
- The adapter **closes `imageProxy` on every path**. Register one analyzer with `STRATEGY_KEEP_ONLY_LATEST` and do not close the frame again.
- `viewWidth` / `viewHeight` are the current `PreviewView` size. If either is 0, the callback returns null.
- The returned box is in **PreviewView space** after a center-crop (`ScaleType.FILL_CENTER`). `mirrorX` is only for a front camera. The rear-camera demo leaves it false.
- Null means no face, or the face is outside the crop. Draw the static thirds target. Do not invent a moving box.
- Dependency A adds: `implementation("com.google.mlkit:face-detection:16.1.7")`

`FaceDetectionAdapter.kt` and `BitmapLutRepository.kt` are Android-only. The JVM tests compile the rest of `app/src/main/java` and skip those two files. Do not also package the `brain` jar into the app; that project tests these same sources.

### Guide

`GuideEngine.suggest(faceBox, steady)` returns exactly one `LiveGuide`.

- Target `(0.67, 0.33)`. READY when the face center is within ±0.08 x and ±0.10 y. Otherwise ALIGN. Null is NO_FACE.
- Messages match the frozen mocks. `source` is `offline_rules`.
- Hints do not say "move left" or "move right".
- `steady` is accepted and does not add a second tip.
- `GuideSmoother` eases the box and emits about 4 times per second. A face appearing or disappearing is delivered immediately.

### LUT

The full downloaded library is in `app/src/main/assets/luts/`. `LutPack.looks` is the selectable list, loaded one file at a time.

- `warm_film` is the downloaded `Cinematic_04` look
- `cool_cinema` is the downloaded `Cinematic_08` look
- `identity_4.cube` is for the identity check and is not a user preset

The demo chips stay `warm_film` and `cool_cinema`. Shot review still may only suggest those or `none`. The other ids include camera-log and film-negative conversions. Those are available in the list, and they are a poor match for a normal phone JPEG.

`BitmapLutRepository.fromAssets(assets).apply(bitmap, lutId, intensity)`

- Default intensity is `0.7`.
- Returns a new bitmap. Does not modify or recycle the original.
- Long edge above 1024 px is graded at 1024. Call this off the main thread.
- Unknown ids throw `UnknownLutException`.
- Warm Film and Cool Cinema are the SDR looks for a normal JPEG. This is a still-photo grade, not a live GPU preview.

### Offline coaching

`OfflineCoach.review(guide)` when Analyze cannot reach the server.

`source` is `offline_rules`. The summary is prefixed `Offline tip`. Never label that response as Gemini.

## Coordinate space

The guide target is on the **displayed** preview, origin top-left, both axes 0..1.

1. Sensor buffer → upright. Use the frame's `rotationDegrees` (clockwise, 0/90/180/270). ML Kit already returns an upright box when `InputImage.fromMediaImage(image, rotation)` is used. The adapter normalizes that upright box. Do not rotate it a second time.
2. Upright frame → PreviewView. Center-crop: scale by `max(viewW / analysisW, viewH / analysisH)`, then remove the centered overflow. A face in the cropped-away margin becomes null.
3. Front camera only: mirror x.

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

`GET /health` → `{"status":"ok","aiMode":"mock"}`

`POST /v1/shot-review` multipart field `image` plus header `X-Demo-Token`.

- `AI_MODE=mock` returns `contracts/shot-review.mock.json` with `source=demo_mock`.
- `AI_MODE=gemini` calls Gemini only when `GEMINI_API_KEY` is set on the server. One request at a time. Timeout is about 12 seconds.
- Missing key or any other mode returns 503. The app should show `OfflineCoach`, not a fake Gemini success.
- 401 bad token, 400 not a JPEG, 413 over 2 MB, 429 already busy, 502 provider failure. The response body does not include the key, the image, or the raw provider error.

Do not put `GEMINI_API_KEY`, `DEMO_TOKEN`, or a service-account JSON in the app or in git. Cloud Run deploy stays manual until the offline camera path is already working:

```
gcloud run deploy frameai-coach --source . --region us-central1 \
  --max-instances 1 --min-instances 0 --cpu 1 --memory 512Mi \
  --set-env-vars AI_MODE=gemini,MODEL_ID=gemini-2.5-flash \
  --set-secrets GEMINI_API_KEY=frameai-gemini-key:latest,DEMO_TOKEN=frameai-demo-token:latest
```

Confirm the project, billing, and whether unauthenticated access is actually required before answering the deploy prompts.
