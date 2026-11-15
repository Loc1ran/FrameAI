import pytest
from fastapi.testclient import TestClient
import main
from validate import validate_live_coach

@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("AI_MODE", "mock")
    monkeypatch.setenv("DEMO_TOKEN", "localtest")
    return TestClient(main.app)

def send(client, mode):
    return client.post('/v1/live-coach?composition=' + mode,
        headers={'X-Demo-Token': 'localtest'},
        files={'image': ('frame.jpg', b'\xff\xd8\xff\xd9', 'image/jpeg')})

def test_scene_mock_is_explicit_not_fake_analysis(client):
    result = send(client, 'scene')
    assert result.status_code == 200
    assert result.headers["X-FrameAI-Composition"] == "scene"
    body = validate_live_coach(result.json())
    assert body['source'] == 'demo_mock'
    assert body['isReady'] is False
    assert body['zoom'] == 'hold'
    assert 'Demo' in body['subject']

def test_unknown_mode_rejected(client):
    assert send(client, 'made-up-mode').status_code == 422

def test_scene_routes_to_contextual_coach(client, monkeypatch):
    monkeypatch.setenv('AI_MODE', 'gemini')
    monkeypatch.setattr(main, '_gemini_configured', lambda: True)
    received = []
    def coach(image, key, model, composition='subject'):
        received.append(composition)
        return {**main._LIVE_MOCK, 'source': 'gemini', 'subject': 'Golfer, club and ball'}
    monkeypatch.setattr(main, 'coach_image', coach)
    assert send(client, 'scene').json()['subject'] == 'Golfer, club and ball'
    assert send(client, 'subject').status_code == 200
    assert received == ['scene', 'subject']
