FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY requirements.lock ./requirements.lock
RUN python -m pip install --no-cache-dir --requirement requirements.lock

COPY app ./app
COPY migrate_user_profiles.py ./migrate_user_profiles.py

RUN groupadd --gid 10001 jatrail \
    && useradd --uid 10001 --gid jatrail --no-create-home --shell /usr/sbin/nologin jatrail

USER 10001:10001

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD ["python", "-c", "import os, urllib.request; host=os.environ.get('JATRAIL_ALLOWED_HOSTS', 'localhost').split(',')[0].strip(); request=urllib.request.Request('http://127.0.0.1:8000/health', headers={'Host': host}); urllib.request.urlopen(request, timeout=3)"]

# The container is reachable only through Caddy. Trust its forwarded scheme and
# client headers so HTTPS URLs and security behavior use the original request.
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips=*"]
