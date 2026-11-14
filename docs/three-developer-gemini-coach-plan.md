# FrameAI: three-developer plan

The camera coaches a person, a background, or a landscape. A face is not required. Gemini compares one frame with a short list of reference patterns and tells the user to zoom in, zoom out, or hold. The phone changes zoom only after Apply zoom.

There is no Gemini API key. The Google login on the machine that runs the server is the credential. Trial credit is spent only by project `project-640ec6f8-36a8-48b1-a57`. Do not enable pay-as-you-go. Do not deploy Cloud Run. Do not upgrade the billing account.

## How the team shares Gemini

Other developers never receive a key and never log into that Cloud project.

- On their own laptops they run `AI_MODE=mock`. `POST /v1/live-coach` returns `contracts/live-coach.mock.json` with `source=demo_mock`. No credit is used.
- A real Gemini answer is available only by calling the server on the PC that already completed the Google login. The request header is `X-Demo-Token`. That token is not a Google key. It only allows a call to this team's server.
- The Android app never contains `GEMINI_API_KEY`, `DEMO_TOKEN`, or a credential file.
- `server/.env` and the Google credential file stay off git.

If that PC is off, or the phone is in airplane mode, the app keeps coaching from on-device rules and the label stays `offline_rules`. A cloud answer is labeled `gemini`. A mock answer is labeled `demo_mock`.

## What is already running

On the credit PC, `server/.env` has `AI_MODE=gemini`, `GOOGLE_CLOUD_PROJECT=project-640ec6f8-36a8-48b1-a57`, `GOOGLE_CLOUD_LOCATION=global`, and `MODEL_ID=gemini-3.5-flash-lite`. Gemini 2.5 is retired for this new project, so the server does not call it. The model endpoint is `global`.

`GET /health` returns `geminiConfigured: true` when the login and the project id are both present. `POST /v1/live-coach` and `POST /v1/shot-review` accept one JPEG field named `image`, max 2 MB, plus `X-Demo-Token`. One Gemini call at a time. Timeout is 12 seconds. Errors are 401, 400, 413, 429, 503, and 502. The server does not log the image or the token.

Reference cards live in `server/references.json`. The schema is `contracts/live-coach.schema.json`. The phone does not call this endpoint yet.

## What the user sees

1. Rear camera, full preview, rule-of-thirds grid.
2. Every few seconds the phone sends one small JPEG to `/v1/live-coach`.
3. The reply names the scene (`portrait`, `landscape`, or `other`) and a subject, even when there is no face.
4. One instruction, max 120 characters. Zoom, horizon, closer, and farther are allowed. "Move left" and "move right" are not.
5. Apply zoom uses `targetZoomRatio` when `zoom` is `in` or `out`. `hold` does not move the lens.
6. The matched pattern is shown by its id, not by a downloaded photo.
7. Capture keeps the original JPEG. Warm Film or Cool Cinema is a separate edit.

## Contract

`LiveGuide` and `ShotReview` stay as they are. Live coaching is a separate object.

```json
{
  "sceneType": "portrait",
  "subject": "person",
  "referenceId": "portrait_space_left",
  "message": "Zoom in. The person is too small for this portrait.",
  "zoom": "in",
  "targetZoomRatio": 1.6,
  "targetX": 0.67,
  "targetY": 0.33,
  "subjectX": 0.42,
  "subjectY": 0.48,
  "isReady": false,
  "source": "gemini"
}
```

`referenceId` is one of `portrait_space_left`, `portrait_space_right`, `landscape_low_horizon`, `landscape_high_horizon`, `landscape_subject_small`, `street_layer`, or `none`. Those cards are written patterns the team owns. Do not scrape photos from the web.

## Who is who

A, B, and C are three people. Each has six tasks. Developer C is us: we are the only ones with this PC and with the Realme, because the phone is plugged in here. A and B write code on their own laptops against mock JSON. They do not get a Cloud login, and they do not take the phone.

## Developer A — a person in the frame

1. When a face is present, decide clipped, too small, too large, or on a third.
2. Map that result to `portrait_space_left` or `portrait_space_right`, with a target point and a `zoom` of `in`, `out`, or `hold`.
3. Draw the face area, the target, and the message. No fixed corner circle.
4. Read the portrait mock and show its `message` and `referenceId` on the preview.
5. Tests: clipped face, small face, large face, and a face already on the third.
6. Open a pull request. C is the one who installs it on the phone.

## Developer B — the place around the subject

1. From the frame, read which side is brighter and whether the light is warm or cool.
2. With no face and a bright top, call it a landscape and put the horizon on a third.
3. With no face and no horizon, say "Point at a subject or a horizon."
4. Draw the horizon line when `sceneType` is `landscape`.
5. Choose `warm_film` or `cool_cinema` from that light reading. Tests cover bright sky, empty frame, and warm versus cool.
6. Open a pull request. C is the one who installs it on the phone.

## Developer C — us, this PC and the phone

1. Push the current coach server on a new branch, not onto `main`, so A and B can start.
2. Add `suggestedLutId` to the live-coach schema and to the Gemini reply.
3. On the phone, send one JPEG every 4 seconds to `http://127.0.0.1:8080/v1/live-coach` through `adb reverse`. No key in the app.
4. On the phone, add Apply zoom. `in` and `out` call `setZoomRatio`. `hold` does not move the lens.
5. If our server does not answer, show A and B's on-device result and label it `offline_rules`. Keep the last Gemini reply for 8 seconds first.
6. Run three scenes on the Realme: a person who is too small, a landscape, and airplane mode. Confirm a live reply says `source=gemini`, and airplane mode does not. Pay-as-you-go stays off.

## Order

1. C pushes the branch.
2. A and B open their pull requests against mock JSON.
3. C merges, installs on the Realme, opens the USB tunnel, and runs the three scenes.
4. Stop. No Cloud Run, no billing upgrade, no API key.

## Out of scope

- A Gemini API key in the app or in git
- Pay-as-you-go, or billing on any project other than `project-640ec6f8-36a8-48b1-a57`
- Scraped example photos
- A live video stream to Gemini
- The full LUT library in the UI
- The front camera
- Accounts
- Zoom that moves the lens before the user taps Apply zoom
