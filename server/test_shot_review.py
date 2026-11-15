import pytest
from fastapi.testclient import TestClient
from jsonschema.exceptions import ValidationError

import main
from validate import (
    accept_director,
    load_json,
    normalize_gemini_payload,
    normalize_live_coach,
    validate_live_coach,
    validate_live_guide,
    validate_shot_review,
)

JPEG = b"\xff\xd8\xff\xd9"


@pytest.fixture(autouse=True)
def demo_env(monkeypatch):
    monkeypatch.setenv("AI_MODE", "mock")
    monkeypatch.setenv("DEMO_TOKEN", "localtest")


@pytest.fixture
def client():
    return TestClient(main.app)


def test_frozen_contracts_match_schema():
    for name in ("live-guide.mock.json", "live-guide.ready.mock.json", "live-guide.noface.mock.json"):
        validate_live_guide(load_json(name))
    mock = validate_shot_review(load_json("shot-review.mock.json"))
    offline = validate_shot_review(load_json("shot-review.offline.json"))
    assert mock["source"] == "demo_mock"
    assert offline["source"] == "offline_rules"
    assert mock["suggestedLutId"] in {"warm_film", "cool_cinema", "none"}


def test_unknown_lut_is_rejected():
    payload = load_json("shot-review.mock.json")
    payload["suggestedLutId"] = "moody"
    with pytest.raises(ValidationError):
        validate_shot_review(payload)


def test_gemini_normalizer_forces_source_and_drops_extras():
    payload = load_json("shot-review.offline.json")
    payload["source"] = "offline_rules"
    payload["debug"] = "do not keep"
    cleaned = normalize_gemini_payload(payload)
    assert cleaned["source"] == "gemini"
    assert "debug" not in cleaned


def test_health(client, monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.delenv("GOOGLE_CLOUD_PROJECT", raising=False)
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["aiMode"] == "mock"
    assert body["geminiConfigured"] is False


def test_mock_review_matches_contract(client):
    response = client.post(
        "/v1/shot-review",
        headers={"X-Demo-Token": "localtest"},
        files={"image": ("sample.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 200
    body = response.json()
    validate_shot_review(body)
    assert body["source"] == "demo_mock"
    assert body == load_json("shot-review.mock.json")


def test_missing_or_wrong_token(client):
    files = {"image": ("sample.jpg", JPEG, "image/jpeg")}
    assert client.post("/v1/shot-review", files=files).status_code == 401
    denied = client.post("/v1/shot-review", headers={"X-Demo-Token": "nope"}, files=files)
    assert denied.status_code == 401


def test_rejects_non_jpeg_and_oversize(client):
    headers = {"X-Demo-Token": "localtest"}
    png = client.post(
        "/v1/shot-review",
        headers=headers,
        files={"image": ("sample.png", b"\x89PNG", "image/png")},
    )
    assert png.status_code == 400
    fake = client.post(
        "/v1/shot-review",
        headers=headers,
        files={"image": ("sample.jpg", b"not-a-jpeg", "image/jpeg")},
    )
    assert fake.status_code == 400
    huge = b"\xff\xd8" + (b"0" * (2 * 1024 * 1024))
    oversized = client.post(
        "/v1/shot-review",
        headers=headers,
        files={"image": ("sample.jpg", huge, "image/jpeg")},
    )
    assert oversized.status_code == 413


def test_gemini_without_key_is_not_a_fake_success(client, monkeypatch):
    monkeypatch.setenv("AI_MODE", "gemini")
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.delenv("GOOGLE_CLOUD_PROJECT", raising=False)
    response = client.post(
        "/v1/shot-review",
        headers={"X-Demo-Token": "localtest"},
        files={"image": ("sample.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 503
    assert "summary" not in response.json()


def test_gemini_busy_does_not_call_the_model(client, monkeypatch):
    monkeypatch.setenv("AI_MODE", "gemini")
    monkeypatch.setenv("GEMINI_API_KEY", "test-key-not-used")
    assert main._gemini_lock.acquire(blocking=False)
    try:
        response = client.post(
            "/v1/shot-review",
            headers={"X-Demo-Token": "localtest"},
            files={"image": ("sample.jpg", JPEG, "image/jpeg")},
        )
        assert response.status_code == 429
    finally:
        main._gemini_lock.release()


def test_mock_live_coach_matches_contract(client):
    response = client.post(
        "/v1/live-coach",
        headers={"X-Demo-Token": "localtest"},
        files={"image": ("sample.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 200
    body = response.json()
    validate_live_coach(body)
    assert body["source"] == "demo_mock"
    assert body["suggestedLutId"] in {"warm_film", "cool_cinema", "none"}
    assert body == load_json("live-coach.mock.json")


def test_live_coach_rejects_an_unknown_lut():
    payload = load_json("live-coach.mock.json")
    payload["suggestedLutId"] = "moody"
    with pytest.raises(ValidationError):
        validate_live_coach(payload)
    payload["source"] = "gemini"
    cleaned = normalize_live_coach(payload)
    assert cleaned["suggestedLutId"] == "none"


def test_live_coach_fills_a_missing_lut_with_none():
    payload = load_json("live-coach.mock.json")
    del payload["suggestedLutId"]
    cleaned = normalize_live_coach(payload)
    assert cleaned["suggestedLutId"] == "none"
    assert cleaned["source"] == "gemini"


def test_live_coach_rejects_an_unknown_reference():
    payload = load_json("live-coach.mock.json")
    payload["referenceId"] = "from_the_internet"
    payload["source"] = "gemini"
    with pytest.raises(ValidationError):
        normalize_live_coach(payload)


def test_live_coach_without_key_is_not_a_fake_success(client, monkeypatch):
    monkeypatch.setenv("AI_MODE", "gemini")
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.delenv("GOOGLE_CLOUD_PROJECT", raising=False)
    response = client.post(
        "/v1/live-coach",
        headers={"X-Demo-Token": "localtest"},
        files={"image": ("sample.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 503
    assert "message" not in response.json()


def test_live_coach_keeps_a_different_artistic_direction():
    payload = load_json("live-coach.mock.json")
    payload.update({
        "message": "Take one step to your right to clear the pole.",
        "photographyStyle": "street",
        "compositionPreset": "centered",
        "primaryIssue": "distracting_background",
        "actionType": "step_right",
        "actionReason": "A pole lines up behind the subject.",
        "confidence": 0.9,
        "source": "gemini",
    })
    cleaned = accept_director(normalize_live_coach(payload), "Move slightly left.")
    assert cleaned["message"] == "Take one step to your right to clear the pole."
    assert "Move slightly left" not in cleaned["message"]
    assert cleaned["actionType"] == "step_right"
    assert cleaned["compositionPreset"] == "none"
    assert cleaned["confidence"] == 0.9
    validate_live_coach(cleaned)


def test_invalid_director_action_does_not_steer():
    payload = load_json("live-coach.mock.json")
    payload["actionType"] = "fly"
    payload["confidence"] = 0.99
    payload["compositionPreset"] = "centered"
    cleaned = normalize_live_coach(payload)
    assert cleaned["actionType"] == "hold"
    assert cleaned["compositionPreset"] == "none"
    assert cleaned["confidence"] == 0
    validate_live_coach(cleaned)


@pytest.mark.parametrize("action", ["orbit_left", "orbit_right", "step_left", "lower_camera", "move_closer"])
def test_viewpoint_is_not_a_zoom_target_or_completed_composition(action):
    payload = load_json("live-coach.mock.json")
    payload.update({
        "actionType": action,
        "compositionPreset": "centered",
        "zoom": "in",
        "targetZoomRatio": 2,
        "isReady": True,
        "message": "Try moving around the car to show more of its side.",
        "actionReason": "The current head-on view makes the car look flat.",
        "confidence": 0.8,
        "source": "gemini",
    })
    cleaned = normalize_live_coach(payload)
    validate_live_coach(cleaned)
    assert cleaned["actionType"] == action
    assert cleaned["compositionPreset"] == "none"
    assert cleaned["zoom"] == "hold"
    assert cleaned["targetZoomRatio"] == 1
    assert cleaned["isReady"] is False


def test_low_confidence_direction_is_not_replaced_by_the_phone_cue():
    payload = load_json("live-coach.mock.json")
    payload.update({
        "message": "Turn toward the window for softer light.",
        "photographyStyle": "portrait",
        "compositionPreset": "none",
        "primaryIssue": "backlit",
        "actionType": "change_lighting",
        "actionReason": "The face is darker than the bright background.",
        "confidence": 0.42,
        "source": "gemini",
    })
    cleaned = accept_director(normalize_live_coach(payload), "Move slightly left.")
    assert cleaned["message"] == "Turn toward the window for softer light."
    assert cleaned["confidence"] == 0.42
    assert cleaned["actionType"] == "change_lighting"
    validate_live_coach(cleaned)


def test_coach_prompt_lets_the_model_choose_the_action():
    from gemini_client import build_coach_prompt

    prompt = build_coach_prompt("subject", instruction="Move slightly left.", style="cinematic")
    assert "must start with this exact instruction" not in prompt
    assert "Do not recommend a different move" not in prompt
    assert "cinematic" in prompt
    assert "Move slightly left." in prompt
    scene = build_coach_prompt("scene", style="landscape")
    assert "landscape" in scene
    assert "must start with this exact instruction" not in scene


def test_shot_review_keeps_ten_phone_looks():
    payload = load_json("shot-review.mock.json")
    payload["source"] = "gemini"
    payload["suggestedLutId"] = "cinematic_05"
    payload["candidateLutIds"] = [
        "warm_film",
        "warm_film",
        "alexa_logc_to_rec709",
        "cool_cinema",
        "cinematic_01",
        "cinematic_02",
        "cinematic_03",
        "cinematic_04",
        "cinematic_06",
        "cinematic_07",
        "cinematic_08",
        "cinematic_09",
        "not_a_lut",
    ]
    cleaned = normalize_gemini_payload(payload)
    assert cleaned["candidateLutIds"][0] == "cinematic_05"
    assert len(cleaned["candidateLutIds"]) == 10
    assert cleaned["candidateLutIds"].count("warm_film") == 1
    assert "alexa_logc_to_rec709" not in cleaned["candidateLutIds"]
    assert "not_a_lut" not in cleaned["candidateLutIds"]
    validate_shot_review(cleaned)


def test_unconfigured_mode(client, monkeypatch):
    monkeypatch.setenv("AI_MODE", "off")
    response = client.post(
        "/v1/shot-review",
        headers={"X-Demo-Token": "localtest"},
        files={"image": ("sample.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 503
