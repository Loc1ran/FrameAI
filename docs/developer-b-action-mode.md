# Developer B: Action mode

Branch: `feat/b-action-best-shot`

Action mode is for moving subjects such as cars, aircraft, sports, and animals.
One shutter press captures five JPEG originals with CameraX in low-latency mode.
The requests run sequentially to avoid overlapping `ImageCapture` operations.

When AI is enabled, the phone sends resized review copies to
`POST /v1/action-best-shot`. Gemini compares the whole burst and returns a
zero-based `selectedIndex`, a short reason, and `source=gemini`. The prompt
prioritizes the meaningful action moment, subject sharpness, visible body parts
and equipment, movement direction, and avoidance of accidental clipping.

The originals never leave the phone. Only review-sized copies are uploaded.
After selection, the phone keeps the chosen original and deletes the unused
burst files. A mock response is never treated as an AI decision. With AI off,
an unavailable server, or an invalid response, the phone falls back to a local
Laplacian-variance sharpness score. If even local ranking fails, it preserves
the middle captured frame.

## Verification

- `server`: pytest covers a five-frame request, burst bounds, mock labeling,
  and routing all frames to the Gemini selector.
- `brain`: JVM tests cover local sharpness ranking.
- Phone testing is still required for actual burst speed, camera stability,
  model selection quality, lifecycle interruption, and deletion of unused files.

The current CameraX implementation is a short sequence of still captures, not a
high-frame-rate hardware burst. If it is too slow on the Realme, the next step is
to keep a small ring buffer from `ImageAnalysis` and encode five nearby frames
when the shutter is pressed.
