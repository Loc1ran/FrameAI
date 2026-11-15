import pytest
from fastapi.testclient import TestClient

import main
from gemini_client import normalize_action_pick

JPEG = b"\xff\xd8\xff\xd9"


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_MODE", "mock")
    monkeypatch.setenv("DEMO_TOKEN", "localtest")
    return TestClient(main.app)


def burst(count=5):
    return [("images", (f"frame_{i}.jpg", JPEG, "image/jpeg")) for i in range(count)]


def test_mock_selects_valid_middle_frame_without_claiming_ai(client):
    response = client.post(
        "/v1/action-best-shot",
        headers={"X-Demo-Token": "localtest"},
        files=burst(),
    )
    assert response.status_code == 200
    assert response.json()["selectedIndex"] == 2
    assert response.json()["source"] == "demo_mock"
    assert response.json()["usable"] is False
    assert "no image analysis" in response.json()["reason"]


def test_requires_a_bounded_burst(client):
    headers = {"X-Demo-Token": "localtest"}
    assert client.post("/v1/action-best-shot", headers=headers, files=burst(1)).status_code == 400
    assert client.post("/v1/action-best-shot", headers=headers, files=burst(9)).status_code == 400


def test_routes_all_frames_to_gemini(client, monkeypatch):
    monkeypatch.setenv("AI_MODE", "gemini")
    monkeypatch.setattr(main, "_gemini_configured", lambda: True)
    received = []

    def choose(images, key, model):
        received.append(len(images))
        return {"selectedIndex": 4, "usable": True, "reason": "Strongest action moment.", "source": "gemini"}

    monkeypatch.setattr(main, "select_action_best", choose)
    response = client.post(
        "/v1/action-best-shot",
        headers={"X-Demo-Token": "localtest"},
        files=burst(),
    )
    assert response.status_code == 200
    assert response.json()["selectedIndex"] == 4
    assert received == [5]


def test_eight_candidates_are_supported(client):
    response = client.post("/v1/action-best-shot", headers={"X-Demo-Token": "localtest"}, files=burst(8))
    assert response.status_code == 200


def test_preserves_all_blurred_verdict():
    result = normalize_action_pick({"selectedIndex": 2, "usable": False, "reason": "Bottle is blurred in every frame."}, 8)
    assert result["usable"] is False
    assert result["selectedIndex"] == 2


@pytest.mark.parametrize("patch", [
    {"usable": None}, {"usable": "true"}, {"selectedIndex": True},
    {"selectedIndex": -1}, {"selectedIndex": 8}, {"reason": ""},
])
def test_rejects_unreliable_selection(patch):
    payload = {"selectedIndex": 0, "usable": True, "reason": "Sharp moving subject."}
    payload.update(patch)
    with pytest.raises(ValueError):
        normalize_action_pick(payload, 8)
