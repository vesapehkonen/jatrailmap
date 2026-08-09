import pytest

from app.config import Settings
from app.database import mongo_client_credentials


def test_mongo_credentials_can_be_loaded_from_secret_files(tmp_path):
    username = tmp_path / "username"
    password = tmp_path / "password"
    username.write_text("jatrail-admin\n", encoding="utf-8")
    password.write_text("private-password\n", encoding="utf-8")
    settings = Settings(
        mongodb_username_file=str(username), mongodb_password_file=str(password)
    )

    assert mongo_client_credentials(settings) == {
        "username": "jatrail-admin",
        "password": "private-password",
    }


def test_mongo_credential_files_must_be_configured_together(tmp_path):
    username = tmp_path / "username"
    username.write_text("jatrail-admin", encoding="utf-8")

    with pytest.raises(ValueError, match="configured together"):
        mongo_client_credentials(Settings(mongodb_username_file=str(username)))


def test_mongo_credential_files_must_not_be_empty(tmp_path):
    username = tmp_path / "username"
    password = tmp_path / "password"
    username.write_text("", encoding="utf-8")
    password.write_text("private-password", encoding="utf-8")

    with pytest.raises(ValueError, match="must not be empty"):
        mongo_client_credentials(
            Settings(
                mongodb_username_file=str(username),
                mongodb_password_file=str(password),
            )
        )
