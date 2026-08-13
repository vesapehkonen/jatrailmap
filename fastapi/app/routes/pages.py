from pathlib import Path

from bson import ObjectId
from fastapi import APIRouter, Depends, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates

from ..auth import optional_user_id

router = APIRouter()
templates = Jinja2Templates(directory=str(Path(__file__).resolve().parent.parent / "templates"))


@router.get("/privacy", response_class=HTMLResponse, include_in_schema=False)
def privacy_page(
    request: Request,
    user_id: ObjectId | None = Depends(optional_user_id),
) -> HTMLResponse:
    return templates.TemplateResponse(
        request,
        "privacy.html",
        {"authenticated": user_id is not None},
    )
