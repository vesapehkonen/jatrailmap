from app.main import app


def registered_routes() -> set[tuple[str, str]]:
    return {
        (route.path, method)
        for route in app.routes
        for method in getattr(route, "methods", set())
    }


def test_versioned_upload_and_track_routes_follow_api_convention():
    routes = registered_routes()
    assert ("/api/v1/trails", "POST") in routes
    assert ("/api/v1/trails/{trail_id}/track", "GET") in routes
    assert ("/api/v1/android/trails", "POST") not in routes
    assert ("/trail/{trail_id}/track", "GET") not in routes
    assert ("/addtrail", "POST") not in routes


def test_root_favicon_fallback_is_registered():
    assert ("/favicon.ico", "GET") in registered_routes()
