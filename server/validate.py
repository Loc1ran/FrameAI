"""ShotReview checks against the frozen contract."""

from __future__ import annotations

import json
from pathlib import Path

from jsonschema import Draft202012Validator
from jsonschema.exceptions import ValidationError

ROOT = Path(__file__).resolve().parents[1]
CONTRACTS = ROOT / "contracts"


def _local_schema(name: str) -> dict:
    schema = json.loads((CONTRACTS / name).read_text(encoding="utf-8"))
    schema.pop("$schema", None)
    schema.pop("$id", None)
    return schema


_LUT_LOOKS = json.loads((CONTRACTS / "luts.json").read_text(encoding="utf-8"))["looks"]
LUT_IDS = {item["id"] for item in _LUT_LOOKS} | {"none"}
PHONE_LUT_IDS = {item["id"] for item in _LUT_LOOKS if item["phoneJpeg"]}
SHOT_REVIEW_SCHEMA = _local_schema("shot-review.schema.json")
LIVE_GUIDE_SCHEMA = _local_schema("live-guide.schema.json")
LIVE_COACH_SCHEMA = _local_schema("live-coach.schema.json")
SHOT_REVIEW_VALIDATOR = Draft202012Validator(SHOT_REVIEW_SCHEMA)
LIVE_GUIDE_VALIDATOR = Draft202012Validator(LIVE_GUIDE_SCHEMA)
LIVE_COACH_VALIDATOR = Draft202012Validator(LIVE_COACH_SCHEMA)

ALLOWED_SHOT_KEYS = {
    "sceneType",
    "summary",
    "improvement",
    "principle",
    "suggestedLutId",
    "lutReason",
    "candidateLutIds",
    "source",
}

ALLOWED_COACH_KEYS = {
    "sceneType",
    "subject",
    "referenceId",
    "message",
    "zoom",
    "targetZoomRatio",
    "targetX",
    "targetY",
    "subjectX",
    "subjectY",
    "isReady",
    "suggestedLutId",
    "source",
    "photographyStyle",
    "compositionPreset",
    "primaryIssue",
    "actionType",
    "actionReason",
    "confidence",
}

PHOTOGRAPHY_STYLES = {"auto", "cinematic", "portrait", "street", "landscape"}
COMPOSITION_PRESETS = {
    "none",
    "centered",
    "negative_space_left",
    "negative_space_right",
    "low_horizon",
    "high_horizon",
    "leading_space",
    "environmental",
    "street_layer",
}
PRIMARY_ISSUES = {
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
}
DIRECTOR_ACTIONS = {
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
}
PHYSICAL_ACTIONS = {
    "step_left",
    "step_right",
    "move_closer",
    "move_farther",
    "lower_camera",
    "raise_camera",
    "change_lighting",
    "expose_for_subject",
}


def validate_shot_review(payload: dict) -> dict:
    SHOT_REVIEW_VALIDATOR.validate(payload)
    return payload


def validate_live_guide(payload: dict) -> dict:
    LIVE_GUIDE_VALIDATOR.validate(payload)
    return payload


def validate_live_coach(payload: dict) -> dict:
    LIVE_COACH_VALIDATOR.validate(payload)
    return payload


def _confidence(value: object) -> float:
    try:
        number = float(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return 0.0
    if number != number or number in (float("inf"), float("-inf")):
        return 0.0
    if number < 0:
        return 0.0
    if number > 1:
        return 1.0
    return number


def sanitize_director(cleaned: dict) -> dict:
    """Keep a supported director decision. An unknown action becomes a no-op the phone ignores."""
    style = cleaned.get("photographyStyle")
    cleaned["photographyStyle"] = style if style in PHOTOGRAPHY_STYLES else "auto"
    preset = cleaned.get("compositionPreset")
    cleaned["compositionPreset"] = preset if preset in COMPOSITION_PRESETS else "none"
    issue = cleaned.get("primaryIssue")
    cleaned["primaryIssue"] = issue if issue in PRIMARY_ISSUES else "none"
    action = cleaned.get("actionType")
    if action not in DIRECTOR_ACTIONS:
        cleaned["actionType"] = "hold"
        cleaned["compositionPreset"] = "none"
        cleaned["confidence"] = 0
    else:
        cleaned["actionType"] = action
        cleaned["confidence"] = _confidence(cleaned.get("confidence"))
    reason = cleaned.get("actionReason")
    if not isinstance(reason, str):
        reason = ""
    cleaned["actionReason"] = " ".join(reason.split())[:160]
    if cleaned["actionType"] in PHYSICAL_ACTIONS:
        cleaned["compositionPreset"] = "none"
    message = cleaned.get("message")
    if isinstance(message, str):
        cleaned["message"] = " ".join(message.split())[:120]
    return cleaned


def accept_director(payload: dict, instruction: str = "") -> dict:
    """A valid artistic direction stands on its own. The on-device cue is not copied over it."""
    del instruction
    return payload


def top_phone_luts(raw: object, suggested: object) -> list[str]:
    """Keep at most 10 phone-photo looks, with the chosen look first."""
    found: list[str] = []
    if isinstance(raw, list):
        for item in raw:
            if isinstance(item, str) and item in PHONE_LUT_IDS and item not in found:
                found.append(item)
            if len(found) == 10:
                break
    if isinstance(suggested, str) and suggested in PHONE_LUT_IDS:
        found = [suggested, *[item for item in found if item != suggested]]
    return found[:10]


def normalize_gemini_payload(payload: object) -> dict:
    if not isinstance(payload, dict):
        raise ValidationError("model output was not a JSON object")
    cleaned = {key: payload[key] for key in ALLOWED_SHOT_KEYS if key in payload}
    cleaned["source"] = "gemini"
    suggested = cleaned.get("suggestedLutId")
    if suggested not in PHONE_LUT_IDS:
        suggested = None
    ranked = top_phone_luts(payload.get("candidateLutIds"), suggested)
    if ranked:
        cleaned["candidateLutIds"] = ranked
        cleaned["suggestedLutId"] = ranked[0]
    else:
        cleaned.pop("candidateLutIds", None)
        if cleaned.get("suggestedLutId") not in LUT_IDS:
            cleaned["suggestedLutId"] = "none"
    return validate_shot_review(cleaned)


def normalize_live_coach(payload: object) -> dict:
    if not isinstance(payload, dict):
        raise ValidationError("model output was not a JSON object")
    cleaned = {key: payload[key] for key in ALLOWED_COACH_KEYS if key in payload}
    cleaned["source"] = "gemini"
    if "subjectX" not in cleaned:
        cleaned["subjectX"] = None
    if "subjectY" not in cleaned:
        cleaned["subjectY"] = None
    if cleaned.get("suggestedLutId") not in LUT_IDS:
        cleaned["suggestedLutId"] = "none"
    sanitize_director(cleaned)
    return validate_live_coach(cleaned)


def load_json(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))
