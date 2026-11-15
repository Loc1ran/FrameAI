# Developer B: Action mode

Branch: `feat/b-action-best-shot`

Action mode is for moving subjects such as cars, aircraft, sports, and animals.
One shutter press first takes a temporary metering photo, then captures eight
JPEG candidates with CameraX in low-latency mode. The requests run sequentially
to avoid overlapping `ImageCapture` operations. Eight frames means more choices,
not a higher capture rate or one photo per millisecond.

On cameras advertising manual sensor support, Action reads the metering photo's
EXIF exposure and ISO, requests about 1/1000 second (keeps an already shorter
exposure), and raises ISO to compensate. Sensor limits may allow up to 1/500
second. If compensation would lose more than one stop of brightness, or the
camera cannot provide manual exposure/metering, it reports a capture error.
The saved candidates' EXIF must confirm an exposure no slower than approximately
1/500 second. Prior Camera2 overrides are restored after the burst. The temporary
metering photo is deleted. Camera mode, ratio, zoom and AI selection cannot be
changed while capturing/ranking.

When AI is enabled, the phone sends resized review copies to
`POST /v1/action-best-shot`. Gemini compares the whole burst and returns a
zero-based `selectedIndex`, `usable`, a short reason, and `source=gemini`. Subject
sharpness is required before considering the action moment, visible body parts
and equipment, movement direction, and avoidance of accidental clipping. A sharp
background must not justify selecting a blurred moving subject. This judgment
is probabilistic and based on resized copies, not a guarantee of sharpness.

The originals never leave the phone. Only review-sized copies are uploaded.
After selection, the phone keeps the chosen original and deletes the unused
burst files. A mock response is never treated as an AI decision. With AI off,
the phone uses a local whole-image Laplacian-variance score, labeled as ranking
overall detail; it cannot establish that a particular moving subject is sharp.
With AI on, an unavailable server, invalid response, or `usable=false` reports
an error and retains the originals in app storage without opening a supposed
best shot. Local ranking failures and interrupted bursts also retain completed
originals. There is no automatic middle-frame fallback. Retained failed bursts
do not yet have an in-app recovery browser or automatic cleanup policy.

Deploy the updated server together with the app: older responses without
`usable` are rejected. Existing clients can still read the added response field.

## Verification

- `server`: pytest covers burst bounds including eight candidates, mock labeling,
  routing all frames to the Gemini selector, and usability verdict validation.
- Android unit tests cover exposure/ISO compensation, insufficient light,
  missing EXIF values and sensor limits.
- `brain`: JVM tests cover local sharpness ranking.
- The full debug app compiles with Android SDK 34 and produces
  `app/build/outputs/apk/debug/app-debug.apk`.
- Phone testing is still required for actual burst speed, camera stability,
  model selection quality, lifecycle interruption, and deletion of unused files.

The current CameraX implementation is a short sequence of still captures, not a
high-frame-rate hardware burst. If it is too slow on the Realme, the next step is
to keep a small ring buffer from `ImageAnalysis` and encode five nearby frames
when the shutter is pressed. Short exposure is still needed to prevent subject
motion blur in those frames. Fast shutter does not correct missed focus, and
high ISO can add noise. Test a swinging object in daylight and indoor light;
verify exposure metadata, autofocus, error messages and return to normal exposure
before claiming motion is reliably frozen on a particular phone.
