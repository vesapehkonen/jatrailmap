from pathlib import Path
from xml.etree import ElementTree


WORKSPACE = Path(__file__).resolve().parents[2]
SVG_NAMESPACE = "http://www.w3.org/2000/svg"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"


def test_web_and_android_reuse_exact_trail_symbol_path():
    sprite = ElementTree.parse(WORKSPACE / "fastapi/app/static/icons.svg").getroot()
    symbol = next(
        item
        for item in sprite.findall(f"{{{SVG_NAMESPACE}}}symbol")
        if item.get("id") == "trail"
    )
    source_path = symbol.find(f"{{{SVG_NAMESPACE}}}path").get("d")
    assert symbol.get("viewBox") == "0 0 24 24"

    web_icon = ElementTree.parse(WORKSPACE / "fastapi/app/static/trail-icon.svg").getroot()
    assert web_icon.get("viewBox") == "0 0 32 32"
    assert web_icon.find(f"{{{SVG_NAMESPACE}}}path").get("d") == source_path

    android_icon = ElementTree.parse(
        WORKSPACE / "android/app/src/main/res/drawable/ic_jatrail.xml"
    ).getroot()
    android_path = android_icon.find("group/path").get(f"{{{ANDROID_NAMESPACE}}}pathData")
    assert android_icon.get(f"{{{ANDROID_NAMESPACE}}}viewportWidth") == "32"
    assert android_icon.get(f"{{{ANDROID_NAMESPACE}}}viewportHeight") == "32"
    assert android_path == source_path
