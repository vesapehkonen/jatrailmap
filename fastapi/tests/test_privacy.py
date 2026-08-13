from pathlib import Path


TEMPLATE = Path(__file__).resolve().parents[1] / "app" / "templates" / "privacy.html"


def test_privacy_policy_contains_date_and_contact():
    policy = TEMPLATE.read_text()

    assert "JaTrail Privacy Policy" in policy
    assert "August 13, 2026" in policy
    assert 'href="mailto:support@jatrail.com"' in policy
    assert "GPS location data" in policy
    assert "self-hosted server" in policy
