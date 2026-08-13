from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request, status
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pymongo import MongoClient
from pymongo.database import Database
from starlette.middleware.trustedhost import TrustedHostMiddleware

from .auth import CSRF_COOKIE, SESSION_COOKIE, csrf_cookie_update
from .config import Settings, get_settings
from .database import connect, ensure_indexes
from .routes.auth import router as auth_router
from .routes.accounts import router as accounts_router
from .routes.groups import router as groups_router
from .routes.trails import router as trails_router
from .routes.admin import router as admin_router
from .routes.pages import router as pages_router

BASE_DIR = Path(__file__).resolve().parent


def database_is_ready(database: Database[Any]) -> bool:
    try:
        database.command("ping")
    except Exception:
        return False
    return True


def health_response(database: Database[Any]) -> JSONResponse:
    if not database_is_ready(database):
        return JSONResponse(
            {"status": "unavailable", "database": "unavailable"},
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        )
    return JSONResponse({"status": "ok", "database": "ok"})


def create_app(settings: Settings | None = None, database: Database[Any] | None = None) -> FastAPI:
    app_settings = settings or get_settings()

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        client: MongoClient[Any] | None = None
        if database is None:
            client = connect(app_settings)
            application.state.db = client[app_settings.mongodb_database]
        else:
            application.state.db = database
        ensure_indexes(application.state.db, app_settings.session_max_age_seconds)
        yield
        if client is not None:
            client.close()

    application = FastAPI(title="JaTrail", lifespan=lifespan)
    application.state.settings = app_settings
    application.add_middleware(TrustedHostMiddleware, allowed_hosts=app_settings.allowed_host_list)
    application.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")

    @application.get("/favicon.ico", include_in_schema=False)
    def favicon() -> FileResponse:
        return FileResponse(BASE_DIR / "static" / "favicon.ico", media_type="image/x-icon")

    @application.get("/health", include_in_schema=False)
    def health(request: Request) -> JSONResponse:
        return health_response(request.app.state.db)

    @application.middleware("http")
    async def security_middleware(request: Request, call_next: Any) -> Any:
        request.state.is_admin = False
        length = request.headers.get("content-length")
        # Keep room for multipart boundaries and manifest metadata.
        request_limit = (app_settings.max_upload_bytes * 4 // 3) + (1024 * 1024)
        if length and length.isdigit() and int(length) > request_limit:
            if request.url.path == "/api/v1/trails":
                return JSONResponse(
                    {
                        "status": "error",
                        "error_code": "request_too_large",
                        "message": "The multipart HTTP request exceeds the server upload limit.",
                        "details": {
                            "content_length_bytes": int(length),
                            "limit_bytes": request_limit,
                        },
                    },
                    status_code=413,
                )
            return JSONResponse({"detail": "Request body too large"}, status_code=413)
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "DENY"
        cookie_csrf = request.cookies.get(CSRF_COOKIE, "")
        csrf_value = csrf_cookie_update(
            request.app.state.db,
            request.cookies.get(SESSION_COOKIE),
            cookie_csrf,
        )
        if csrf_value:
            response.set_cookie(
                CSRF_COOKIE,
                csrf_value,
                secure=app_settings.secure_cookies,
                httponly=False,
                samesite="strict",
                path="/",
            )
        return response

    application.include_router(auth_router)
    application.include_router(accounts_router)
    application.include_router(groups_router)
    application.include_router(trails_router)
    application.include_router(admin_router)
    application.include_router(pages_router)
    return application


app = create_app()
