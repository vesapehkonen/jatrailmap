import math
from datetime import datetime, timezone
from typing import Any

EARTH_RADIUS_MILES = 3958.7613


def parse_timestamp(value: Any) -> datetime | None:
    if isinstance(value, datetime):
        return value
    if not isinstance(value, str) or not value.strip():
        return None
    text = value.strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(text)
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
        return parsed
    except ValueError:
        for pattern in ("%m/%d/%Y", "%Y-%m-%d", "%a %b %d %H:%M:%S %Z %Y"):
            try:
                return datetime.strptime(value.strip(), pattern)
            except ValueError:
                continue
    return None


def coordinates(location: dict[str, Any]) -> list[float] | None:
    value = location.get("loc", {}).get("coordinates")
    if not isinstance(value, list) or len(value) < 2:
        return None
    try:
        return [float(item) for item in value]
    except (TypeError, ValueError):
        return None


def segment_miles(first: list[float], second: list[float]) -> float:
    lon1, lat1 = map(math.radians, first[:2])
    lon2, lat2 = map(math.radians, second[:2])
    delta_lat = lat2 - lat1
    delta_lon = lon2 - lon1
    value = (
        math.sin(delta_lat / 2) ** 2
        + math.cos(lat1) * math.cos(lat2) * math.sin(delta_lon / 2) ** 2
    )
    return EARTH_RADIUS_MILES * 2 * math.atan2(math.sqrt(value), math.sqrt(1 - value))


def trail_statistics(locations: list[dict[str, Any]]) -> dict[str, Any]:
    distance = 0.0
    previous: list[float] | None = None
    timestamps: list[datetime] = []
    elevation_points = 0
    elevation_gain_meters = 0.0
    previous_elevation: float | None = None
    elevations: list[float] = []
    for location in locations:
        point = coordinates(location)
        if point is not None:
            if previous is not None:
                distance += segment_miles(previous, point)
            previous = point
            if len(point) >= 3 and math.isfinite(point[2]):
                elevation_points += 1
                elevations.append(point[2])
                if previous_elevation is not None and point[2] > previous_elevation:
                    elevation_gain_meters += point[2] - previous_elevation
                previous_elevation = point[2]
        timestamp = parse_timestamp(location.get("timestamp"))
        if timestamp is not None:
            timestamps.append(timestamp)
    elapsed_seconds: int | None = None
    if len(timestamps) >= 2:
        elapsed_seconds = max(0, int((max(timestamps) - min(timestamps)).total_seconds()))
    return {
        "distance_miles": distance,
        "elapsed_seconds": elapsed_seconds,
        "elevation_points": elevation_points,
        "elevation_gain_feet": elevation_gain_meters * 3.28084,
        "minimum_elevation_feet": min(elevations) * 3.28084 if elevations else None,
        "maximum_elevation_feet": max(elevations) * 3.28084 if elevations else None,
    }


def friendly_duration(seconds: int | None) -> str:
    if seconds is None:
        return "Unavailable"
    hours, remainder = divmod(seconds, 3600)
    minutes, secs = divmod(remainder, 60)
    if hours:
        return f"{hours} h {minutes} min"
    if minutes:
        return f"{minutes} min {secs} sec"
    return f"{secs} sec"


def friendly_date(value: Any) -> str:
    parsed = parse_timestamp(value)
    if parsed is None:
        return str(value) if value else "Unknown"
    return f"{parsed.strftime('%b')} {parsed.day}, {parsed.year}"
