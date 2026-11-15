# FrameAI: three-developer plan

The app takes a picture of anything: a person, a place, an object, or a background. A face is not required.

Gemini looks at one JPEG of the frame that is on the screen right now. The user does not upload a sample of the picture they want. The reference cards are written patterns, not photos.

There is no Gemini API key. The Google login on this PC is the credential. Trial credit is spent only by project `project-640ec6f8-36a8-48b1-a57`. Do not enable pay-as-you-go. Do not deploy Cloud Run.

## What Gemini receives

`POST /v1/live-coach` takes one JPEG field named `image`. That file is the current camera frame, long edge 768 px, sent at most every 4 seconds. The header is `X-Demo-Token`. That token is not a Google key.

Gemini returns one instruction: the scene (`portrait`, `landscape`, or `other`), the subject, a reference id, and `zoom` of `in`, `out`, or `hold`. The phone moves the lens only after Apply zoom.

The reference ids are `portrait_space_left`, `portrait_space_right`, `landscape_low_horizon`, `landscape_high_horizon`, `landscape_subject_small`, `street_layer`, and `none`. They live in `server/references.json`. Do not scrape photos from the web.

If the server does not answer, the phone keeps coaching from the same live frame on the device. That label is `offline_rules`. A cloud answer is `gemini`. A mock answer is `demo_mock`.

## Already done

- The server accepts the current-frame JPEG. Model `gemini-3.5-flash-lite`, location `global`. `GET /health` reports `geminiConfigured` without showing a key.
- The phone coaches any subject without Gemini: a person on a third, a horizon on a grid line, a brighter side left open, or an even frame that is ready to shoot.
- The Realme reaches this PC over USB with `adb reverse tcp:8080 tcp:8080`, then calls `http://127.0.0.1:8080`. Same Wi-Fi is not required.
- A and B do not get the Cloud login. On their laptops, `AI_MODE=mock` returns `contracts/live-coach.mock.json` and spends no credit.

The phone sends the current frame JPEG at most every 4 seconds. Apply zoom waits for a tap. A missed reply keeps the last Gemini picture for 8 seconds, then the on-device coach continues.

## What the preview shows

The live instruction is a picture, not a paragraph. The `message` field stays in the JSON for tests. The camera does not print it.

- The preview names one action, including the direction: Move left, Move right, Move up, Move down, Zoom in, Zoom out, Step closer, Step back, Tilt up, or Tilt down. It does not print the message sentence.
- The solid frame tracks the subject, whether that subject is a person or an object. Corner brackets stay on it.
- Move shows the grid, that tracked frame, and a same-size dashed frame at the destination. The label says which way to move the phone: left, right, up, or down. Moving the phone that way slides the subject into the dashed frame. Zoom is hidden.
- Zoom hides the grid. The solid frame is the subject now. The dashed frame is the exact size to make. The label names the factor, such as Zoom in 2× or Zoom out 1.6×, and that label applies it.
- If the lens cannot zoom any farther, the label says Step closer or Step back.
- A horizon hides the grid. A solid line is the horizon now, a dashed line is the goal, and the label says Tilt up or Tilt down.
- When they match, the tracked frame turns green and a check appears. No label.

## Who is who

A, B, and C are three people. Each has six tasks. Developer C is us. We are the only ones with this PC and the Realme. A and B write code on their own laptops. They do not take the phone.

## Developer A — a person in the frame

1. When a face is present, decide clipped, too small, too large, or on a third.
2. Map that result to `portrait_space_left` or `portrait_space_right`, with `zoom` of `in`, `out`, or `hold`.
3. Show Move when the person needs a new place. Show Zoom in or Zoom out when only the size is wrong. Do not print the message sentence.
4. When the person already fills the goal, show the green check.
5. Tests: clipped, small, large, and already on a third.
6. Open a pull request. C installs it on the phone.

## Developer B — the place around the subject

1. Read which side is brighter and whether the light is warm or cool.
2. With no face and a clear horizon, put that horizon on a grid line.
3. With no face and no horizon, the frame can still be shot. Show the green check, not a sentence.
4. Draw the horizon as a solid line, a dashed goal line, and the shaded band. The label says Tilt up or Tilt down. Hide the rule-of-thirds grid.
5. Choose `warm_film` or `cool_cinema` from the light. Test bright sky, an even frame, and warm versus cool.
6. Open a pull request. C installs it on the phone.

## Developer C — us, this PC and the phone

1. Keep the server on this PC. Do not create an API key. Do not turn on pay-as-you-go.
2. Add `suggestedLutId` (`warm_film`, `cool_cinema`, or `none`) to the live-coach reply.
3. On the phone, send the current frame JPEG to `http://127.0.0.1:8080/v1/live-coach` through the USB tunnel. No key in the app.
4. On the phone, Zoom in and Zoom out call `setZoomRatio`. Move and hold do not move the lens. If the lens is already at its limit, say Step closer or Step back.
5. If the server does not answer, keep the on-device coach and label it `offline_rules`. Hold the last Gemini reply for 8 seconds first.
6. Run three scenes on the Realme: a person, a place with a horizon, and an object on a table. Confirm the live reply says `source=gemini`. Unplug the server and confirm the same scenes still coach on the device.

## Order

1. A and B build against mock JSON and open pull requests.
2. C merges those requests, installs them on the Realme, and opens the USB tunnel.
3. C sends the current frame to Gemini and runs the three scenes.
4. Stop. No Cloud Run, no billing upgrade, no API key.

## Out of scope

- Uploading a sample of the picture the user wishes they had
- A Gemini API key in the app or in git
- Pay-as-you-go, or billing on any project other than `project-640ec6f8-36a8-48b1-a57`
- Scraped example photos
- A live video stream to Gemini
- The full LUT library in the UI
- The front camera
- Accounts
- Zoom that moves the lens before the user taps Apply zoom
