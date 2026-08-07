from datetime import datetime
from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, field_validator


class GeoPoint(BaseModel):
    type: Literal["Point"] = "Point"
    coordinates: list[float]

    @field_validator("coordinates")
    @classmethod
    def valid_coordinates(cls, value: list[float]) -> list[float]:
        if len(value) not in (2, 3):
            raise ValueError("coordinates must contain longitude, latitude, and optional altitude")
        longitude, latitude = value[:2]
        if not -180 <= longitude <= 180 or not -90 <= latitude <= 90:
            raise ValueError("coordinates are outside valid longitude/latitude ranges")
        return value


class LocationRecord(BaseModel):
    timestamp: str
    loc: GeoPoint

    @field_validator("timestamp")
    @classmethod
    def valid_timestamp(cls, value: str) -> str:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return value


class PictureUpload(BaseModel):
    timestamp: str
    filename: str = ""
    picturename: str = ""
    description: str = ""
    loc: GeoPoint
    file: str

    @field_validator("timestamp")
    @classmethod
    def valid_timestamp(cls, value: str) -> str:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return value


class TrailInfo(BaseModel):
    type: Literal["TrailInfo"]
    access: str | None = None
    date: str | None = None
    trailname: str = Field(min_length=1, max_length=200)
    locationname: str = Field(default="", max_length=300)
    description: str = Field(default="", max_length=10_000)


class UserInfo(BaseModel):
    type: Literal["UserInfo"]
    username: str = Field(min_length=1, max_length=200)
    password: str = Field(min_length=1, max_length=1024)


class LocationCollection(BaseModel):
    type: Literal["LocationCollection"]
    locations: list[LocationRecord] = Field(default_factory=list)


class PictureCollection(BaseModel):
    type: Literal["PictureCollection"]
    pictures: list[PictureUpload] = Field(default_factory=list)


LegacyEntry = Annotated[
    Union[TrailInfo, UserInfo, LocationCollection, PictureCollection],
    Field(discriminator="type"),
]


class LegacyTrailUpload(BaseModel):
    model_config = ConfigDict(extra="ignore")
    newtrail: list[LegacyEntry]


class TrailUpdate(BaseModel):
    trailname: str = Field(min_length=1, max_length=200)
    location: str = Field(default="", max_length=300)
    description: str = Field(default="", max_length=10_000)


class CoordinateUpdate(BaseModel):
    longitude: float = Field(ge=-180, le=180)
    latitude: float = Field(ge=-90, le=90)


class PictureUpdate(CoordinateUpdate):
    picturename: str = Field(default="", max_length=300)
    description: str = Field(default="", max_length=10_000)


class VisibilityUpdate(BaseModel):
    access: Literal["public", "private", "group"]
    groups: list[str] = Field(default_factory=list)


class ProfileUpdate(BaseModel):
    display_name: str = Field(default="", max_length=100)
    profile_location: str = Field(default="", max_length=200)
    show_name_on_public_trails: bool = False
    show_location_on_public_trails: bool = False
    email: str = Field(default="", max_length=320)
    current_password: str = Field(min_length=1, max_length=1024)

    @field_validator("email")
    @classmethod
    def valid_email(cls, value: str) -> str:
        if value and ("@" not in value or value.startswith("@") or value.endswith("@")):
            raise ValueError("email address is invalid")
        return value


class PasswordUpdate(BaseModel):
    current_password: str = Field(min_length=1, max_length=1024)
    new_password: str = Field(min_length=8, max_length=1024)


class AccountDelete(BaseModel):
    current_password: str = Field(min_length=1, max_length=1024)


class GroupCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    members: list[str] = Field(default_factory=list)


class GroupUpdate(GroupCreate):
    pass


class AdminAccountUpdate(BaseModel):
    suspended: bool
    admin_password: str = Field(min_length=1, max_length=1024)


class AdminQuotaUpdate(BaseModel):
    account_storage_mb: int | None = Field(default=None, ge=1, le=100_000)
    image_mb: int | None = Field(default=None, ge=1, le=1_000)
    photos_per_trail: int | None = Field(default=None, ge=1, le=10_000)
    upload_mb: int | None = Field(default=None, ge=1, le=10_000)
    admin_password: str = Field(min_length=1, max_length=1024)


class TrailModeration(BaseModel):
    reason: str = Field(min_length=3, max_length=1000)
    admin_password: str = Field(min_length=1, max_length=1024)


class AdminApprovalUpdate(BaseModel):
    approved: bool
    admin_password: str = Field(min_length=1, max_length=1024)


class AdminRoleUpdate(BaseModel):
    role: Literal["user", "admin"]
    admin_password: str = Field(min_length=1, max_length=1024)


class RegistrationSettingsUpdate(BaseModel):
    enabled: bool
    approval_required: bool
    account_storage_mb: int = Field(ge=1, le=100_000)
    image_mb: int = Field(ge=1, le=1_000)
    photos_per_trail: int = Field(ge=1, le=10_000)
    upload_mb: int = Field(ge=1, le=10_000)
    admin_password: str = Field(min_length=1, max_length=1024)


class AdminPasswordConfirmation(BaseModel):
    admin_password: str = Field(min_length=1, max_length=1024)


class PasswordResetUpdate(BaseModel):
    new_password: str = Field(min_length=8, max_length=1024)
