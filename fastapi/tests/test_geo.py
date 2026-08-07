from app.geo import friendly_date, friendly_duration, trail_statistics


def test_trail_statistics_distance_duration_and_elevation():
    locations = [
        {
            "timestamp": "2025-01-02T10:00:00Z",
            "loc": {"type": "Point", "coordinates": [-122.0, 47.0, 100.0]},
        },
        {
            "timestamp": "2025-01-02T11:02:03Z",
            "loc": {"type": "Point", "coordinates": [-122.0, 47.01, 125.0]},
        },
    ]
    result = trail_statistics(locations)
    assert 0.68 < result["distance_miles"] < 0.70
    assert result["elapsed_seconds"] == 3723
    assert result["elevation_points"] == 2
    assert 81.9 < result["elevation_gain_feet"] < 82.1
    assert round(result["minimum_elevation_feet"]) == 328
    assert round(result["maximum_elevation_feet"]) == 410


def test_friendly_date_and_duration():
    assert friendly_date("2025-01-02T10:00:00Z") == "Jan 2, 2025"
    assert friendly_duration(3723) == "1 h 2 min"
    assert friendly_duration(None) == "Unavailable"
