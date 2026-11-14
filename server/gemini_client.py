"""One Gemini still-photo review. The API key stays in the server environment."""

from __future__ import annotations

import json

from validate import normalize_gemini_payload

PROMPT = (
    "You are FrameAI, a photography coach looking at one still photo. "
    "Return JSON only. sceneType must be portrait, landscape, street, or other. "
    "suggestedLutId must be warm_film, cool_cinema, or none. "
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
        "suggestedLutId": {"type": "STRING", "enum": ["warm_film", "cool_cinema", "none"]},
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


class GeminiNotConfigured(RuntimeError):
    pass


def review_image(image: bytes, api_key: str, model_id: str) -> dict:
    if not api_key.strip():
        raise GeminiNotConfigured("GEMINI_API_KEY is empty")
    from google import genai
    from google.genai import types

    client = genai.Client(
        api_key=api_key,
        http_options=types.HttpOptions(timeout=12_000),
    )
    response = client.models.generate_content(
        model=model_id,
        contents=[
            types.Part.from_bytes(data=image, mime_type="image/jpeg"),
            PROMPT,
        ],
        config=types.GenerateContentConfig(
            temperature=0.2,
            response_mime_type="application/json",
            response_schema=GEMINI_SCHEMA,
        ),
    )
    text = getattr(response, "text", None)
    if not text:
        raise ValueError("empty model response")
    return normalize_gemini_payload(json.loads(text))
