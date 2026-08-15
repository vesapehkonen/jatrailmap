from pathlib import Path


TEMPLATE = Path(__file__).resolve().parents[1] / "app" / "templates" / "delete_data.html"


def test_data_deletion_page_covers_android_server_and_contact():
    instructions = TEMPLATE.read_text()

    assert "Delete data from the Android app" in instructions
    assert "Clear storage" in instructions
    assert "does not delete trails already uploaded" in instructions
    assert "Delete a trail from the JaTrail website" in instructions
    assert "Delete your server account and all data" in instructions
    assert 'href="mailto:support@jatrail.com"' in instructions
