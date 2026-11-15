import pytest
from fastapi.testclient import TestClient
from jsonschema.exceptions import ValidationError

import main
from validate import (
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
    assert body == load_json("live-coach.mock.json")


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


def test_unconfigured_mode(client, monkeypatch):
    monkeypatch.setenv("AI_MODE", "off")
    response = client.post(
        "/v1/shot-review",
        headers={"X-Demo-Token": "localtest"},
        files={"image": ("sample.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 503
