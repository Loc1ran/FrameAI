"""FrameAI shot-review gateway.

Local mock:
    AI_MODE=mock DEMO_TOKEN=localtest uvicorn main:app --host 127.0.0.1 --port 8080

Gemini mode uses GEMINI_API_KEY, or Application Default Credentials plus GOOGLE_CLOUD_PROJECT.
The process does not log image bytes or the demo token.
"""

from __future__ import annotations

import hmac
import logging
import os
import threading
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile, Response

from gemini_client import GeminiNotConfigured, coach_image, review_image
from validate import align_live_coach, load_json, validate_live_coach, validate_shot_review

MAX_BYTES = 2 * 1024 * 1024
JPEG_TYPES = {"image/jpeg", "image/jpg", "application/octet-stream"}
logger = logging.getLogger("frameai.shot_review")
_gemini_lock = threading.Lock()


def _load_local_env() -> None:
    """Read server/.env or the repo .env. A real environment variable wins. Never log values."""
    candidates = (
        Path(__file__).resolve().parent / ".env",
        Path(__file__).resolve().parents[1] / ".env",
    )
    for path in candidates:
        if not path.is_file():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and value and key not in os.environ:
                os.environ[key] = value


_load_local_env()

app = FastAPI(title="FrameAI coach")

_MOCK = validate_shot_review(load_json("shot-review.mock.json"))
if _MOCK.get("source") != "demo_mock":
    raise RuntimeError("shot-review.mock.json must stay source=demo_mock")

_LIVE_MOCK = validate_live_coach(load_json("live-coach.mock.json"))
if _LIVE_MOCK.get("source") != "demo_mock":
    raise RuntimeError("live-coach.mock.json must stay source=demo_mock")


def _adc_present() -> bool:
    explicit = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "").strip()
    if explicit and Path(explicit).is_file():
        return True
    appdata = os.environ.get("APPDATA", "")
    if not appdata:
        return False
    return (Path(appdata) / "gcloud" / "application_default_credentials.json").is_file()


def _gemini_configured() -> bool:
    if os.environ.get("GEMINI_API_KEY", "").strip():
        return True
    project = os.environ.get("GOOGLE_CLOUD_PROJECT", "").strip()
    return bool(project) and _adc_present()


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "aiMode": os.environ.get("AI_MODE", "mock"),
        "geminiConfigured": _gemini_configured(),
    }


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
    if not _gemini_configured():
        raise HTTPException(status_code=503, detail="AI is not configured")
    if not _gemini_lock.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="AI is busy")
    try:
        model_id = os.environ.get("MODEL_ID", "gemini-3.5-flash-lite").strip() or "gemini-3.5-flash-lite"
        api_key = os.environ.get("GEMINI_API_KEY", "").strip()
        return review_image(payload, api_key, model_id)
    except GeminiNotConfigured:
        raise HTTPException(status_code=503, detail="AI is not configured")
    except Exception as exc:
        logger.warning("shot review failed: %s", type(exc).__name__)
        raise HTTPException(status_code=502, detail="AI request failed")
    finally:
        _gemini_lock.release()


@app.post("/v1/live-coach")
async def live_coach(
    response: Response,
    composition: Literal["subject", "scene"] = "subject",
    instruction: str = Form(default=""),
    image: UploadFile = File(...),
    x_demo_token: str | None = Header(default=None),
) -> dict:
    if not _authorized(x_demo_token):
        raise HTTPException(status_code=401, detail="Unauthorized")
    response.headers["X-FrameAI-Composition"] = composition
    payload = await _read_jpeg(image)
    mode = os.environ.get("AI_MODE", "mock").strip().lower()
    if mode == "mock":
        if composition == "scene":
            return {**_LIVE_MOCK, "subject": "Demo scene — no image analysis",
                    "referenceId": "none", "message": "Demo only: keep the action and its surroundings visible.",
                    "zoom": "hold", "targetZoomRatio": 1, "isReady": False}
        return dict(_LIVE_MOCK)
    if mode != "gemini":
        raise HTTPException(status_code=503, detail="AI is not configured")
    if not _gemini_configured():
        raise HTTPException(status_code=503, detail="AI is not configured")
    if not _gemini_lock.acquire(blocking=False):
        raise HTTPException(status_code=429, detail="AI is busy")
    try:
        model_id = os.environ.get("MODEL_ID", "gemini-3.5-flash-lite").strip() or "gemini-3.5-flash-lite"
        api_key = os.environ.get("GEMINI_API_KEY", "").strip()
        if composition == "scene":
            return coach_image(payload, api_key, model_id, composition="scene")
        return align_live_coach(
            coach_image(payload, api_key, model_id, instruction=instruction),
            instruction,
        )
    except GeminiNotConfigured:
        raise HTTPException(status_code=503, detail="AI is not configured")
    except Exception as exc:
        logger.warning("live coach failed: %s", type(exc).__name__)
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
