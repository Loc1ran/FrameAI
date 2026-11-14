# Frozen contracts (H+0.25)

Do **not** invent field names. Changes require explicit A+B agreement.

## LiveGuide (local coach)

Allowed `kind`: `ALIGN_SUBJECT` | `READY` | `NO_FACE`  
Coordinates are PreviewView-normalized `[0,1]` after crop/rotation/mirror — not raw sensor pixels.  
Default portrait target: `(0.67, 0.33)`.

Mocks: `live-guide.mock.json`, `live-guide.ready.mock.json`, `live-guide.noface.mock.json`

## ShotReview (optional cloud)

Allowed `suggestedLutId`: `warm_film` | `cool_cinema` | `none`  
Allowed `sceneType`: `portrait` | `landscape` | `street` | `other`  
`source` is mandatory so mocks are never labeled as Gemini.

API (optional):

- `GET /health` → `{ "status": "ok", "aiMode": "mock" }`
- `POST /v1/shot-review` multipart JPEG + `X-Demo-Token`
