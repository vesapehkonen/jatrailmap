# JaTrail Docker Compose deployment

The production stack runs Caddy, FastAPI, and MongoDB as separate containers.
Caddy is the only service with published ports. FastAPI is reachable only from
Caddy and MongoDB is isolated on an internal network. Caddy obtains and renews
public TLS certificates automatically and keeps certificate state in named
volumes.

## Small VPS resource profile

The Compose limits target a 1-core, 512 MB RAM, 20 GB SSD VPS: MongoDB receives
352 MB, FastAPI 96 MB, and Caddy 32 MB, for a 480 MB combined container ceiling.
CPU shares total one core. MongoDB uses the minimum supported 0.256 GB
WiredTiger cache and accepts at most 100 simultaneous connections. Docker logs
rotate at 5 MB with two files per service.

MongoDB is pinned to `7.0.39-jammy`. MongoDB 8.0 and newer cannot start on
Linux kernels 6.19 through 7.0.13 because of an upstream TCMalloc
incompatibility. Keep the 7.0 pin while deploying on an affected VPS kernel;
do not change it to MongoDB 8 without first confirming kernel compatibility.

This is MongoDB's practical lower edge and leaves very little memory for Linux
and Docker. Configure at least 1 GB of swap on the VPS to absorb short-lived
startup, health-check, backup, and upgrade peaks. Swap is protection against an
outage, not additional normal operating capacity. Monitor the first real
uploads with `docker stats`; if MongoDB or FastAPI is killed for exceeding its
limit, the dependable fix is increasing VPS RAM to 1 GB or more.

## Initial setup

From the repository root:

```bash
cp deploy/jatrail.env.example deploy/jatrail.env
cp deploy/caddy.env.example deploy/caddy.env
cp deploy/image.env.example deploy/image.env
mkdir -p deploy/secrets
chmod 700 deploy/secrets
```

Set `JATRAIL_SITE_ADDRESS` in `deploy/caddy.env` to the public DNS name, such as
`trails.example.com`. Set `JATRAIL_ALLOWED_HOSTS` in `deploy/jatrail.env` to the
same name and leave `JATRAIL_SECURE_COOKIES=true` in production. The DNS A/AAAA
records must point to this server, and inbound TCP 80 and TCP/UDP 443 must be
allowed. Ports 80 and 443 must not already be occupied by another web server.

Create separate MongoDB administration and JaTrail application credential
files:

```bash
printf '%s' 'jatrail-admin' > deploy/secrets/mongo_root_username
openssl rand -base64 48 > deploy/secrets/mongo_root_password
printf '%s' 'jatrail-app' > deploy/secrets/mongo_app_username
openssl rand -base64 48 > deploy/secrets/mongo_app_password
chmod 644 deploy/secrets/*
```

Keep `deploy/secrets` at mode `0700`. Compose local secrets preserve host
ownership, so the files need mode `0644` for the unprivileged containers to
read them; other host users still cannot traverse the directory.

Validate the configuration:

```bash
docker compose --env-file deploy/image.env -f deploy/compose.yaml config
```

## Deploy a FastAPI image

GitHub Actions publishes each successful `main` build to GHCR with its full
40-character Git commit SHA. Deploy that exact immutable image from the
repository root:

```bash
./deploy/deploy_image.sh FULL_40_CHARACTER_COMMIT_SHA
```

The script verifies that all local environment and MongoDB secret files exist,
pulls only the selected FastAPI image, starts Compose with `--no-build`, waits
for container health checks, and requests the public `/health` endpoint. After
MongoDB is healthy, it synchronizes the database application account password
and `readWrite` role from the existing mounted secret files before FastAPI is
started. It writes the selected SHA to ignored `deploy/image.env` and, after
successful health verification, to ignored `deploy/.deployed-image`. On a
fresh server it also pulls the MongoDB and Caddy images before starting Compose.

For local testing or convenience, the moving tag is accepted explicitly:

```bash
./deploy/deploy_image.sh latest
```

Use a full SHA for production so the deployed FastAPI image is unambiguous.

It never creates or overwrites anything in `deploy/secrets`, never deletes
images, and performs no automatic rollback. If deployment fails, inspect the
reported state and rerun the same SHA after correcting the problem.

For a public GHCR package, no registry login is needed. For a private package,
log in once manually on the VPS with a token limited to `read:packages`:

```bash
docker login ghcr.io
```

The first public start can take a short time while Caddy obtains the
certificate. Follow progress with:

```bash
docker compose --env-file deploy/image.env -f deploy/compose.yaml logs -f caddy web
```

Requests to HTTP are redirected to HTTPS automatically. Caddy stores
certificates and renewal state in `jatrail_caddy_data`; recreating containers
does not discard them.

The FastAPI container health check uses the first hostname from
`JATRAIL_ALLOWED_HOSTS`. Keep the public deployment hostname first in that
setting. Caddy relies on this container health check and normal passive proxy
failure detection rather than a second localhost-based active probe.

## Local HTTP validation

For a machine without a public DNS name, set:

```dotenv
# deploy/caddy.env
JATRAIL_SITE_ADDRESS=http://localhost
JATRAIL_MAX_REQUEST_BODY=150MB
```

and use these development-only application settings:

```dotenv
# deploy/jatrail.env
JATRAIL_SECURE_COOKIES=false
JATRAIL_ALLOWED_HOSTS=localhost,127.0.0.1
```

Then open <http://localhost> or run `curl http://localhost/health`. Do not use
these cookie settings for a public deployment.

## Upload limit coordination

`JATRAIL_MAX_REQUEST_BODY` is a protective proxy ceiling, not an account quota.
Keep it above FastAPI's effective request limit so the Android multipart API
receives JaTrail's structured HTTP error response. With the default
`JATRAIL_MAX_UPLOAD_BYTES=52428800`, the 150 MB proxy ceiling provides ample
multipart overhead. If the FastAPI upload ceiling is increased beyond 100 MiB,
increase the Caddy value as well.

## Routine operations

```bash
docker compose --env-file deploy/image.env -f deploy/compose.yaml restart web
docker compose --env-file deploy/image.env -f deploy/compose.yaml logs --tail=200 caddy web mongo
docker stats --no-stream
docker compose --env-file deploy/image.env -f deploy/compose.yaml down
```

`down` preserves all named volumes. Never run `down --volumes` against data or
certificates you intend to keep.

## Persistent credentials and data

MongoDB initialization credentials are applied only when `mongo_data` is empty.
The root account initializes MongoDB; FastAPI receives only the application
account, which has `readWrite` access to the `jatrail` database. Changing secret
files later does not change users stored in an existing database. Credential
rotation requires changing the MongoDB user password and its secret file
together.

The named MongoDB volumes survive container replacement. They are persistent
storage, not backups. The stack does not automatically import a database
installed on the host; export and restore existing data deliberately before
switching production traffic.

Deployment never creates, replaces, or rotates files under `deploy/secrets`.
Create them manually on the VPS before the first deployment and retain them
across Git pulls and container replacement. They are intentionally excluded
from Git.

## MongoDB backups

Phase 4 backs up only the `jatrail` MongoDB database. It does not copy Docker
volumes, Caddy state, TLS certificates, source code, or any separate generated
files. Images stored in MongoDB are naturally included in the database dump.

Create a compressed archive manually:

```bash
./deploy/backup_mongodb.sh
```

Archives are written atomically to `deploy/backups` with mode `0600`. The
default retention is 14 days. Both settings can be changed for one invocation:

```bash
JATRAIL_BACKUP_DIR=/srv/jatrail-backups \
JATRAIL_BACKUP_RETENTION_DAYS=30 \
./deploy/backup_mongodb.sh
```

For a simple daily schedule, run `crontab -e` as the deployment user and add a
line using absolute paths:

```cron
15 3 * * * JATRAIL_BACKUP_DIR=/srv/jatrail-backups JATRAIL_BACKUP_RETENTION_DAYS=30 /srv/jatrail/deploy/backup_mongodb.sh >> /srv/jatrail-backup.log 2>&1
```

Periodically copy the resulting archives off the VPS using your preferred
manual method. Retention removes only files named `jatrail-*.archive.gz` from
the configured backup directory.

### Restore a backup

Restoring with `--drop` replaces the current collections. Stop FastAPI first,
select the archive deliberately, and keep MongoDB running:

```bash
docker compose --env-file deploy/image.env -f deploy/compose.yaml stop web

docker compose --env-file deploy/image.env -f deploy/compose.yaml exec -T mongo sh -ec '
  mongorestore \
    --host 127.0.0.1:27017 \
    --username "$(cat /run/secrets/mongo_app_username)" \
    --password "$(cat /run/secrets/mongo_app_password)" \
    --authenticationDatabase jatrail \
    --nsInclude "jatrail.*" \
    --drop \
    --archive \
    --gzip
' < deploy/backups/jatrail-YYYYMMDDTHHMMSSZ.archive.gz

docker compose --env-file deploy/image.env -f deploy/compose.yaml start web
```

Check `https://your-domain.example/health` and inspect a few trails after the
restore. A backup is not proven until a restore has been tested.
