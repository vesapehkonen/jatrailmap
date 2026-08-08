# JaTrail

JaTrail is a web and Android application for recording, correcting, and sharing
GPS trails with geotagged photos.

![JaTrail screenshot](jatrailmap.png)

## Active applications

- `fastapi/` — FastAPI web application and JSON API
- `android/` — Android GPS recording and upload application

The current server uses FastAPI, Pydantic, PyMongo, MongoDB, Jinja2 templates,
and plain JavaScript. The Android application uploads trail metadata and photos
through the multipart `POST /api/v1/trails` endpoint.

## Web application setup

Clone the repository and enter the FastAPI project:

```bash
git clone https://github.com/vesapehkonen/jatrail.git
cd jatrail/fastapi
```

Create a virtual environment and install the application:

```bash
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
cp .env.example .env
```

Review `.env`, ensure MongoDB is available, and start the development server:

```bash
.venv/bin/uvicorn app.main:app --reload
```

Run the server tests with:

```bash
.venv/bin/pytest -q
```

## Android application

Open `android/` in Android Studio or use its Gradle wrapper. Configure the
server URL in the application before uploading trails to another environment.

## Legacy servers

The retired Node.js and Spring implementations are preserved under `legacy/`
for historical migration reference only. They must not be run against the
current JaTrail database. See [`legacy/README.md`](legacy/README.md) for details.

## License

JaTrail is licensed under the MIT License. See `LICENSE.txt`.
