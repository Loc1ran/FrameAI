"""FrameAI shot-review gateway.

Local mock:
    AI_MODE=mock DEMO_TOKEN=localtest uvicorn main:app --host 127.0.0.1 --port 8080

Gemini mode needs GEMINI_API_KEY in the server environment only.
The process does not log image bytes or the demo token.
"""

from __future__ import annotations

import hmac
import logging
import os
import threading

from fastapi import FastAPI, File, Header, HTTPException, UploadFile

from gemini_client import GeminiNotConfigured, review_image
from validate import load_json, validate_shot_review

MAX_BYTES = 2 * 1024 * 1024
JPEG_TYPES = {"image/jpeg", "image/jpg", "application/octet-stream"}
logger = logging.getLogger("frameai.shot_review")
_gemini_lock = threading.Lock()

app = FastAPI(title="FrameAI shot review")

_MOCK = validate_shot_review(load_json("shot-review.mock.json"))
if _MOCK.get("source") != "demo_mock":
    raise RuntimeError("shot-review.mock.json must stay source=demo_mock")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "aiMode": os.environ.get("AI_MODE", "mock")}


@app.post("/v1/shot-review")
async def shot_review(
    image: UploadFile = File(...),
    x_demo_token: str | None = Header(default=None),
) -> dict:
    if not _authorized(x_demo_token):
        raise HTTPException(status_code=401, detail="Unauthorized")
    payload = await _read_jpeg(image)
    mode = os.environ.get("AI_MODE", "mock").strip().lower()
    if mode == "mock":
        return dict(_MOCK)
    if mode != "gemini":
        raise HTTPException(status_code=503, detail="AI is not configured")
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        raise HTTPException(status_code=503, detail="AI is not configured")
    if not _gemini_lock.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="AI is busy")
    try:
        model_id = os.environ.get("MODEL_ID", "gemini-2.5-flash").strip() or "gemini-2.5-flash"
        return review_image(payload, api_key, model_id)
    except GeminiNotConfigured:
        raise HTTPException(status_code=503, detail="AI is not configured")
    except Exception as exc:
        logger.warning("shot review failed: %s", type(exc).__name__)
        raise HTTPException(status_code=502, detail="AI request failed")
    finally:
        _gemini_lock.release()


def _authorized(header: str | None) -> bool:
    expected = os.environ.get("DEMO_TOKEN", "")
    if not expected or not header or len(header) != len(expected):
        return False
    return hmac.compare_digest(header, expected)


async def _read_jpeg(image: UploadFile) -> bytes:
    mime = (image.content_type or "").split(";", 1)[0].strip().lower()
    if mime and mime not in JPEG_TYPES:
        raise HTTPException(status_code=400, detail="JPEG is required")
    data = await image.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="Image is too large")
    if len(data) < 3 or data[:2] != b"\xff\xd8":
        raise HTTPException(status_code=400, detail="JPEG is required")
    return data
