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


SHOT_REVIEW_SCHEMA = _local_schema("shot-review.schema.json")
LIVE_GUIDE_SCHEMA = _local_schema("live-guide.schema.json")
SHOT_REVIEW_VALIDATOR = Draft202012Validator(SHOT_REVIEW_SCHEMA)
LIVE_GUIDE_VALIDATOR = Draft202012Validator(LIVE_GUIDE_SCHEMA)

ALLOWED_SHOT_KEYS = {
    "sceneType",
    "summary",
    "improvement",
    "principle",
    "suggestedLutId",
    "lutReason",
    "source",
}


def validate_shot_review(payload: dict) -> dict:
    SHOT_REVIEW_VALIDATOR.validate(payload)
    return payload


def validate_live_guide(payload: dict) -> dict:
    LIVE_GUIDE_VALIDATOR.validate(payload)
    return payload


def normalize_gemini_payload(payload: object) -> dict:
    if not isinstance(payload, dict):
        raise ValidationError("model output was not a JSON object")
    cleaned = {key: payload[key] for key in ALLOWED_SHOT_KEYS if key in payload}
    cleaned["source"] = "gemini"
    return validate_shot_review(cleaned)


def load_json(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))
