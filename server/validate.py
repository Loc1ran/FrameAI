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


LUT_IDS = {
    item["id"]
    for item in json.loads((CONTRACTS / "luts.json").read_text(encoding="utf-8"))["looks"]
} | {"none"}
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


def normalize_gemini_payload(payload: object) -> dict:
    if not isinstance(payload, dict):
        raise ValidationError("model output was not a JSON object")
    cleaned = {key: payload[key] for key in ALLOWED_SHOT_KEYS if key in payload}
    cleaned["source"] = "gemini"
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
    return validate_live_coach(cleaned)


def load_json(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))
