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
PHONE_LOOKS = [item for item in LUT_LOOKS if item["phoneJpeg"]]
PHONE_LUT_IDS = [item["id"] for item in PHONE_LOOKS]
LUT_GUIDE = (
    "The picture is a normal phone JPEG in Rec.709. "
    "suggestedLutId must be the single best grade for the color and subject in this picture, or none to leave the color unchanged.\n"
    "Phone-photo looks:\n"
    + "\n".join(f"- {item['id']}: {item['name']}" for item in PHONE_LOOKS)
    + "\nCamera-log conversions are the wrong grade for a phone JPEG. Do not choose one:\n"
    + "\n".join(f"- {item['id']}: {item['name']}" for item in LUT_LOOKS if not item["phoneJpeg"])
)
SHOT_LUT_GUIDE = (
    "The picture is a normal phone JPEG in Rec.709. "
    "Choose the 10 phone-photo looks that best fit this picture and put the best first. "
    "Return only those ids in candidateLutIds. Do not repeat an id and do not return more than 10. "
    "suggestedLutId is the first of those ids, or none when the color should stay unchanged. "
    "Do not choose a camera-log conversion.\n"
    "Phone-photo looks:\n"
    + "\n".join(f"- {item['id']}: {item['name']}" for item in PHONE_LOOKS)
)

PROMPT = (
    "You are Athena, a photography mentor looking at one finished photograph. "
    "Speak in plain, modern language. Do not use mythology or riddles. "
    "Return JSON only. sceneType must be portrait, landscape, street, or other. "
    f"{SHOT_LUT_GUIDE}\n"
    "summary is what worked: one or two specific strengths that are visible. "
    "improvement is the single most important weakness. If you cannot see a real problem, say the photograph is already strong. "
    "principle is the photography skill this frame practiced, as one concrete next step. "
    "Do not invent people, objects, light, or places. Do not give a numeric quality score. "
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
        "suggestedLutId": {"type": "STRING"},
        "candidateLutIds": {
            "type": "ARRAY",
            "maxItems": 10,
            "items": {"type": "STRING"},
        },
        "lutReason": {"type": "STRING"},
        "source": {"type": "STRING", "enum": ["gemini"]},
    },
    "required": [
        "sceneType",
        "summary",
        "improvement",
        "principle",
        "suggestedLutId",
        "candidateLutIds",
        "lutReason",
        "source",
    ],
}


COACH_PROMPT = (
    "You are Athena, a photography mentor looking at one live camera frame. "
    "Help a beginner make one useful change. Speak in plain, modern language. "
    "Do not use Greek mythology, riddles, or poetic metaphors. "
    "The frame may be a person, a background, or a landscape. A missing face is allowed. "
    "Decide the single most useful photographic change and say why it helps. "
    "Do not describe an arrow someone else already chose. "
    "Return JSON only. Choose the closest reference id from this list, or none:\n"
    f"{REFERENCE_TEXT}\n"
    "sceneType must be portrait, landscape, or other. "
    "zoom must be in, out, or hold. Leave zoom on hold unless the subject is clearly too small or cut off. "
    "targetZoomRatio is from 1 to 8. "
    "A centered subject can be the right composition. Do not move a picture that is already working. "
    "Do not invent people, objects, or light that are not visible. "
    f"{LUT_GUIDE}\n"
    "Set source to gemini."
)

DIRECTOR_FIELDS = (
    "photographyStyle is auto, cinematic, portrait, street, or landscape. Use the requested style when one is given. "
    "The style changes what you value. It does not force the same composition every time. "
    "Cinematic: dramatic perspective, intentional negative space, depth, atmosphere, motivated light. "
    "Portrait: flattering light, eye placement, separation from the background, natural facial proportions. "
    "Street: leading lines, the place around the subject, and a composition that keeps the moment. "
    "Landscape: horizon placement, foreground interest, depth, balance, and natural light. "
    "Auto: choose the approach the picture actually supports. "
    "compositionPreset is none, centered, negative_space_left, negative_space_right, low_horizon, high_horizon, "
    "leading_space, environmental, or street_layer. "
    "negative_space_left puts the subject on the right third with open space on the left. "
    "negative_space_right puts the subject on the left third with open space on the right. "
    "Use none when the change is a step, a height change, or a lighting change rather than a new frame target. "
    "primaryIssue is none, subject_underexposed, backlit, harsh_light, distracting_background, clipped_highlights, "
    "tilted_horizon, weak_separation, empty_space, or already_strong. "
    "actionType is hold, pan_left, pan_right, tilt_up, tilt_down, straighten, step_left, step_right, "
    "move_closer, move_farther, lower_camera, raise_camera, change_lighting, or expose_for_subject. "
    "pan and tilt aim the phone. step, move_closer, move_farther, lower_camera, and raise_camera move the person. "
    "Those are not interchangeable. Use a step or a height change when something behind the subject overlaps them "
    "and sliding the frame would not separate them. "
    "message names the change a beginner should make, under 120 characters, including the photographic reason in everyday words. "
    "actionReason says what is visible and why the change improves the picture, under 160 characters. "
    "Do not answer with only 'move slightly left', 'adjust your position', or 'improve composition'. "
    "confidence is from 0 to 1. Use a value under 0.6 when the picture does not support a change. "
    "targetX and targetY are hints only. The phone recomputes the goal from compositionPreset. "
    "For a portrait, do not pick a grade that would turn skin pink, blue, or severely clipped. "
    "isReady is true only when no useful change is visible. "
    "referenceId matches the preset when one of the listed patterns applies, otherwise none."
)

STYLE_GUIDE = {
    "auto": "Requested style: auto. Choose the photographic approach this frame supports.",
    "cinematic": "Requested style: cinematic. Favor perspective, negative space, depth, and motivated light when the frame supports them.",
    "portrait": "Requested style: portrait. Favor flattering light, eye placement, and separation. A centered face can be correct.",
    "street": "Requested style: street. Favor leading lines and the relationship between the subject and the place.",
    "landscape": "Requested style: landscape. Favor horizon placement, foreground, depth, and natural light. Do not invent a person.",
}

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
        "photographyStyle": {"type": "STRING", "enum": ["auto", "cinematic", "portrait", "street", "landscape"]},
        "compositionPreset": {
            "type": "STRING",
            "enum": [
                "none",
                "centered",
                "negative_space_left",
                "negative_space_right",
                "low_horizon",
                "high_horizon",
                "leading_space",
                "environmental",
                "street_layer",
            ],
        },
        "primaryIssue": {
            "type": "STRING",
            "enum": [
                "none",
                "subject_underexposed",
                "backlit",
                "harsh_light",
                "distracting_background",
                "clipped_highlights",
                "tilted_horizon",
                "weak_separation",
                "empty_space",
                "already_strong",
            ],
        },
        "actionType": {
            "type": "STRING",
            "enum": [
                "hold",
                "pan_left",
                "pan_right",
                "tilt_up",
                "tilt_down",
                "straighten",
                "step_left",
                "step_right",
                "move_closer",
                "move_farther",
                "lower_camera",
                "raise_camera",
                "change_lighting",
                "expose_for_subject",
            ],
        },
        "actionReason": {"type": "STRING"},
        "confidence": {"type": "NUMBER"},
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
        "photographyStyle",
        "compositionPreset",
        "primaryIssue",
        "actionType",
        "actionReason",
        "confidence",
        "source",
    ],
}

ACTION_PROMPT = (
    "You are FrameAI selecting one photograph from a short chronological action burst. "
    "Compare every supplied frame. First require a sharp moving main subject: a sharp stationary "
    "background does not make a blurred bottle, car, or other moving subject usable. Among usable "
    "frames choose the strongest meaningful action moment, visible face/eyes when relevant, complete important body parts "
    "and equipment, and a composition that preserves the direction of movement. Reject frames with "
    "obvious accidental blur, blocked subjects, or important clipping. This mode aims to freeze the "
    "subject, not create intentional subject motion blur. If every subject is blurred or you cannot "
    "confidently assess subject sharpness, return usable=false and explain why. Still return the "
    "least-bad selectedIndex for reference; it will not be automatically kept. Do not invent details "
    "outside the images. Return usable, the zero-based selectedIndex, one reason under 160 characters, and source=gemini."
)

ACTION_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "selectedIndex": {"type": "INTEGER"},
        "usable": {"type": "BOOLEAN"},
        "reason": {"type": "STRING"},
        "source": {"type": "STRING", "enum": ["gemini"]},
    },
    "required": ["selectedIndex", "usable", "reason", "source"],
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


def build_coach_prompt(composition: str, instruction: str = "", style: str = "auto") -> str:
    """Ask for a director decision. An on-device cue is context, not a required sentence."""
    base = SCENE_PROMPT if composition == "scene" else COACH_PROMPT
    chosen = style if style in STYLE_GUIDE else "auto"
    parts = [base, DIRECTOR_FIELDS, STYLE_GUIDE[chosen]]
    move = " ".join(instruction.split())[:120]
    if composition != "scene" and move:
        parts.append(
            "The phone is currently showing this on-device cue as context only: "
            f"{move} "
            "Keep it when the picture supports it. Propose a different action when a better change is visible. "
            "Do not copy the cue unless it is still the best next step."
        )
    return "\n".join(parts)


def coach_image(
    image: bytes,
    api_key: str,
    model_id: str,
    composition: str = "subject",
    instruction: str = "",
    style: str = "auto",
) -> dict:
    client = _client(api_key)
    from google.genai import types

    prompt = build_coach_prompt(composition, instruction, style)
    response = client.models.generate_content(
        model=model_id,
        contents=[
            types.Part.from_bytes(data=image, mime_type="image/jpeg"),
            prompt,
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


def select_action_best(images: list[bytes], api_key: str, model_id: str) -> dict:
    if len(images) < 2:
        raise ValueError("action burst needs at least two frames")
    client = _client(api_key)
    from google.genai import types

    contents = []
    for index, image in enumerate(images):
        contents.append(f"Frame {index}")
        contents.append(types.Part.from_bytes(data=image, mime_type="image/jpeg"))
    contents.append(ACTION_PROMPT)
    response = client.models.generate_content(
        model=model_id,
        contents=contents,
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=ACTION_SCHEMA,
        ),
    )
    text = getattr(response, "text", None)
    if not text:
        raise ValueError("empty model response")
    result = json.loads(text)
    return normalize_action_pick(result, len(images))


def normalize_action_pick(result: dict, frame_count: int) -> dict:
    index = result.get("selectedIndex")
    reason = result.get("reason")
    usable = result.get("usable")
    if type(index) is not int or index not in range(frame_count):
        raise ValueError("selectedIndex outside burst")
    if type(usable) is not bool:
        raise ValueError("missing or invalid action usability")
    if not isinstance(reason, str) or not 1 <= len(reason) <= 160:
        raise ValueError("invalid action reason")
    return {"selectedIndex": index, "usable": usable, "reason": reason, "source": "gemini"}
