import json

from app.config import Settings
from app.main import create_app, health_response


class ReadyDatabase:
    def command(self, name: str) -> dict[str, int]:
        assert name == "ping"
        return {"ok": 1}


class UnavailableDatabase:
    def command(self, name: str) -> None:
        raise RuntimeError("database unavailable")


def test_health_reports_ready_database():
    response = health_response(ReadyDatabase())

    assert response.status_code == 200
    assert json.loads(response.body) == {"status": "ok", "database": "ok"}


def test_health_reports_database_failure_without_leaking_error():
    response = health_response(UnavailableDatabase())

    assert response.status_code == 503
    assert json.loads(response.body) == {
        "status": "unavailable",
        "database": "unavailable",
    }


def test_health_route_is_registered(database):
    application = create_app(
        Settings(secure_cookies=False, allowed_hosts="testserver"), database
    )

    assert any(route.path == "/health" for route in application.routes)
