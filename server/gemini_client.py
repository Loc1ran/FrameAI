"""Gemini still-photo review and live coaching. The API key stays in the server environment."""

from __future__ import annotations

import json
import os
from pathlib import Path

from validate import normalize_gemini_payload, normalize_live_coach

REFERENCES = json.loads((Path(__file__).resolve().parent / "references.json").read_text(encoding="utf-8"))
REFERENCE_TEXT = "\n".join(
    f"- {card['id']} ({card['sceneType']}): {card['pattern']}" for card in REFERENCES
)
LUT_LOOKS = json.loads(
    (Path(__file__).resolve().parents[1] / "contracts" / "luts.json").read_text(encoding="utf-8")
)["looks"]
LUT_IDS = [item["id"] for item in LUT_LOOKS] + ["none"]
LUT_GUIDE = (
    "The picture is a normal phone JPEG in Rec.709. "
    "suggestedLutId must be the single best grade for the color and subject in this picture, or none to leave the color unchanged.\n"
    "Phone-photo looks:\n"
    + "\n".join(f"- {item['id']}: {item['name']}" for item in LUT_LOOKS if item["phoneJpeg"])
    + "\nCamera-log conversions are the wrong grade for a phone JPEG. Do not choose one:\n"
    + "\n".join(f"- {item['id']}: {item['name']}" for item in LUT_LOOKS if not item["phoneJpeg"])
)

PROMPT = (
    "You are FrameAI, a photography coach looking at one still photo. "
    "Return JSON only. sceneType must be portrait, landscape, street, or other. "
    f"{LUT_GUIDE}\n"
    "Give one specific composition improvement a beginner can try on the next frame. "
    "Do not invent people, locations, or camera settings that are not visible. "
    "Do not claim the phone moved. "
    "Set source to gemini. "
    "Keep summary and improvement under 200 characters, principle under 80, and lutReason under 160."
)

GEMINI_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "sceneType": {"type": "STRING", "enum": ["portrait", "landscape", "street", "other"]},
        "summary": {"type": "STRING"},
        "improvement": {"type": "STRING"},
        "principle": {"type": "STRING"},
        "suggestedLutId": {"type": "STRING", "enum": LUT_IDS},
        "lutReason": {"type": "STRING"},
        "source": {"type": "STRING", "enum": ["gemini"]},
    },
    "required": [
        "sceneType",
        "summary",
        "improvement",
        "principle",
        "suggestedLutId",
        "lutReason",
        "source",
    ],
}


COACH_PROMPT = (
    "You are FrameAI, a live photography coach looking at one camera frame. "
    "The frame may be a person, a background, or a landscape. A missing face is allowed. "
    "Return JSON only. Choose the closest reference id from this list, or none:\n"
    f"{REFERENCE_TEXT}\n"
    "sceneType must be portrait, landscape, or other. "
    "zoom must be in, out, or hold. "
    "targetZoomRatio is from 1 to 8. Use hold with the ratio closest to how the frame should stay. "
    "message is one beginner instruction under 120 characters. "
    "Do not say move left or move right. Zoom, horizon, closer, and farther are allowed. "
    "Do not invent people or places that are not visible. "
    f"{LUT_GUIDE}\n"
    "Set source to gemini."
)

SCENE_PROMPT = (
    "You are FrameAI in SCENE mode. Read the entire current image to understand the activity and its setting. "
    "The subject is the meaningful group of elements, not necessarily a person: for golf it may include "
    "the golfer, club, visible ball, and open space toward the range. Infer this only when supported by the image. "
    "Preserve relationships, gaze/action direction, leading lines, and useful background. "
    "A person on the left can be correctly placed when the action and environment extend right. "
    "Never center a person by default or enforce thirds regardless of context. Do not always tilt up or raise the camera. "
    "Distinguish uninformative foreground from ground that contains shoes, a ball, equipment, or meaningful action. "
    "Keep visible heads, hands, feet, equipment, and action space in frame. If something appears clipped, "
    "prioritize restoring it. Do not claim to see objects beyond the frame or invent a hidden ball. "
    "Suggest one small physical adjustment when it has a clear benefit. Raising the phone is translation; "
    "tilting changes its angle. A higher viewpoint with a slight downward tilt MAY reveal background while "
    "keeping feet, but it is not guaranteed; stepping back may be necessary. Never infer a measured phone height "
    "or exact degrees from one image. Explain the action and its purpose in message, under 120 characters. "
    "If composition already supports the activity, hold. When uncertain, ask the user to hold for a clearer view "
    "and set isReady false. Do not give a cinematic score. "
    "Return the live-coach JSON schema. subject names the entire meaningful group in at most 40 characters. "
    "sceneType is portrait, landscape, or other. referenceId is none unless a listed pattern clearly applies. "
    "Use zoom=hold and targetZoomRatio=1: scene advice guides physical movement without moving the lens. "
    "targetX and targetY describe the group's current center, not a forced centering destination. "
    "isReady means the visible composition needs no clear adjustment, not objective aesthetic perfection. "
    f"{LUT_GUIDE}\n"
    "Set source to gemini. "
    f"Supported patterns:\n{REFERENCE_TEXT}"
)

COACH_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "sceneType": {"type": "STRING", "enum": ["portrait", "landscape", "other"]},
        "subject": {"type": "STRING"},
        "referenceId": {
            "type": "STRING",
            "enum": [card["id"] for card in REFERENCES] + ["none"],
        },
        "message": {"type": "STRING"},
        "zoom": {"type": "STRING", "enum": ["in", "out", "hold"]},
        "targetZoomRatio": {"type": "NUMBER"},
        "targetX": {"type": "NUMBER"},
        "targetY": {"type": "NUMBER"},
        "subjectX": {"type": "NUMBER"},
        "subjectY": {"type": "NUMBER"},
        "isReady": {"type": "BOOLEAN"},
        "suggestedLutId": {"type": "STRING", "enum": LUT_IDS},
        "source": {"type": "STRING", "enum": ["gemini"]},
    },
    "required": [
        "sceneType",
        "subject",
        "referenceId",
        "message",
        "zoom",
        "targetZoomRatio",
        "targetX",
        "targetY",
        "isReady",
        "suggestedLutId",
        "source",
    ],
}


class GeminiNotConfigured(RuntimeError):
    pass


def _client(api_key: str):
    """Use an API key when one is set. Otherwise use the Google Cloud ADC login."""
    from google import genai
    from google.genai import types

    options = types.HttpOptions(timeout=12_000)
    key = api_key.strip()
    if key:
        return genai.Client(api_key=key, http_options=options)
    project = os.environ.get("GOOGLE_CLOUD_PROJECT", "").strip()
    location = os.environ.get("GOOGLE_CLOUD_LOCATION", "global").strip() or "global"
    if not project:
        raise GeminiNotConfigured("GEMINI_API_KEY is empty and GOOGLE_CLOUD_PROJECT is empty")
    return genai.Client(vertexai=True, project=project, location=location, http_options=options)


def review_image(image: bytes, api_key: str, model_id: str) -> dict:
    client = _client(api_key)
    from google.genai import types

    response = client.models.generate_content(
        model=model_id,
        contents=[
            types.Part.from_bytes(data=image, mime_type="image/jpeg"),
            PROMPT,
        ],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=GEMINI_SCHEMA,
        ),
    )
    text = getattr(response, "text", None)
    if not text:
        raise ValueError("empty model response")
    return normalize_gemini_payload(json.loads(text))


def coach_image(image: bytes, api_key: str, model_id: str, composition: str = "subject") -> dict:
    client = _client(api_key)
    from google.genai import types

    response = client.models.generate_content(
        model=model_id,
        contents=[
            types.Part.from_bytes(data=image, mime_type="image/jpeg"),
            SCENE_PROMPT if composition == "scene" else COACH_PROMPT,
        ],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=COACH_SCHEMA,
        ),
    )
    text = getattr(response, "text", None)
    if not text:
        raise ValueError("empty model response")
    return normalize_live_coach(json.loads(text))
