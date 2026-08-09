from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    mongodb_uri: str = "mongodb://localhost:27017"
    mongodb_database: str = "jatrail"
    mongodb_username_file: str = ""
    mongodb_password_file: str = ""
    session_max_age_seconds: int = 86_400
    secure_cookies: bool = True
    allowed_hosts: str = "localhost,127.0.0.1"
    max_account_storage_bytes: int = 100 * 1024 * 1024
    max_image_bytes: int = 2 * 1024 * 1024
    max_photos_per_trail: int = 30
    max_upload_bytes: int = 50 * 1024 * 1024

    model_config = SettingsConfigDict(
        env_prefix="JATRAIL_", env_file=".env", extra="ignore"
    )

    @property
    def allowed_host_list(self) -> list[str]:
        return [item.strip() for item in self.allowed_hosts.split(",") if item.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
