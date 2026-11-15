import pytest
from fastapi.testclient import TestClient

import main

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
        return {"selectedIndex": 4, "reason": "Strongest action moment.", "source": "gemini"}

    monkeypatch.setattr(main, "select_action_best", choose)
    response = client.post(
        "/v1/action-best-shot",
        headers={"X-Demo-Token": "localtest"},
        files=burst(),
    )
    assert response.status_code == 200
    assert response.json()["selectedIndex"] == 4
    assert received == [5]
