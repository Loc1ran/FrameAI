# Frozen contracts

Do not invent field names. Schemas in this folder are the source of truth. The copies under `app/src/main/assets/contracts/` must match the mocks listed in `app/build.gradle.kts`.

## LiveGuide (on-device coach)

Allowed `kind`: `ALIGN_SUBJECT` | `READY` | `NO_FACE`  
Coordinates are PreviewView-normalized `[0,1]` after crop, rotation, and mirror.

Mocks: `live-guide.mock.json`, `live-guide.ready.mock.json`, `live-guide.noface.mock.json`

## LiveCoach (optional cloud)

`POST /v1/live-coach?composition=subject|scene`  
Multipart JPEG field `image`, form fields `style` and `instruction`, header `X-Demo-Token`.  
A scene response must include `X-FrameAI-Composition: scene`.

Required fields are in `live-coach.schema.json`. `source` is `gemini` or `demo_mock`.  
Mock: `live-coach.mock.json`

## ShotReview (optional cloud)

`POST /v1/shot-review` with one JPEG field `image` and header `X-Demo-Token`.

`suggestedLutId` is `none` or an id in `luts.json`. The review row keeps at most 10 phone-photo ids (`phoneJpeg: true`). Camera-log conversions are not offered there.  
`source` is mandatory so a mock is never labeled as Gemini.

Mocks: `shot-review.mock.json`, `shot-review.offline.json`

## Action best shot (optional cloud)

`POST /v1/action-best-shot` with 2 to 8 JPEG fields named `images` and header `X-Demo-Token`.

Response: `selectedIndex`, `usable`, `reason`, `source`.  
Mock mode returns the middle frame with `usable: false` and `source: demo_mock`. The phone does not treat that as an AI pick.

## Health

`GET /health` → `{ "status": "ok", "aiMode": "mock", "geminiConfigured": false }`
