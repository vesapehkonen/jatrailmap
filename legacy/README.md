# Legacy JaTrail servers

This directory contains the original Node.js and Spring server implementations.
They are retained only as historical reference for the FastAPI migration and are
no longer maintained or deployed.

The active JaTrail applications are:

- `../fastapi/` — current web application and JSON API
- `../android/` — current Android application

## Important safety notice

Do not run either legacy server against the current JaTrail MongoDB database.
The legacy applications use obsolete authentication and authorization behavior,
the old image representation, old database and field names, and API contracts
that the current Android application no longer uses. Their maintenance scripts
can modify or remove data using assumptions that are no longer valid.

In particular, the legacy applications are not compatible with:

- the current `jatrail` database configuration;
- BSON-binary image storage;
- the multipart `POST /api/v1/trails` upload API;
- current sessions, CSRF protection, quotas, and centralized access checks; or
- current user profile and main-trail-photo fields.

## Contents

- `nodejs/` — original Express/Jade implementation and maintenance scripts
- `spring/` — experimental Spring implementation

Git history preserves these applications. Once they are no longer useful for
migration reference, this entire directory can be removed from the active
branch without losing their history.
