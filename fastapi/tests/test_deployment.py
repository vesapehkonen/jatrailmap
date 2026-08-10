from pathlib import Path

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def test_compose_exposes_only_caddy() -> None:
    compose = yaml.safe_load((REPOSITORY_ROOT / "deploy" / "compose.yaml").read_text())
    services = compose["services"]

    assert "ports" not in services["mongo"]
    assert "ports" not in services["web"]
    assert services["caddy"]["ports"] == ["80:80", "443:443", "443:443/udp"]
    assert compose["networks"]["backend"]["internal"] is True
    assert services["mongo"]["networks"] == ["backend"]
    assert set(services["web"]["networks"]) == {"backend", "proxy"}
    assert services["caddy"]["networks"] == ["proxy"]


def test_caddy_certificate_state_is_persistent() -> None:
    compose = yaml.safe_load((REPOSITORY_ROOT / "deploy" / "compose.yaml").read_text())
    volumes = compose["services"]["caddy"]["volumes"]

    assert "caddy_data:/data" in volumes
    assert "caddy_config:/config" in volumes
    assert "caddy_data" in compose["volumes"]
    assert "caddy_config" in compose["volumes"]


def test_caddy_proxies_to_internal_web_and_limits_request_bodies() -> None:
    caddyfile = (REPOSITORY_ROOT / "deploy" / "Caddyfile").read_text()

    assert "reverse_proxy web:8000" in caddyfile
    assert "Host localhost" in caddyfile
    assert "request_body" in caddyfile
    assert "JATRAIL_MAX_REQUEST_BODY:150MB" in caddyfile


def test_mongodb_backup_is_scoped_and_uses_existing_secrets() -> None:
    script = (REPOSITORY_ROOT / "deploy" / "backup_mongodb.sh").read_text()

    assert "mongodump" in script
    assert "--db jatrail" in script
    assert "--archive" in script
    assert "--gzip" in script
    assert "/run/secrets/mongo_app_username" in script
    assert "/run/secrets/mongo_app_password" in script
    assert "JATRAIL_BACKUP_RETENTION_DAYS" in script
    assert "jatrail-*.archive.gz" in script
    assert "docker volume" not in script


def test_compose_small_vps_resource_budget() -> None:
    compose = yaml.safe_load((REPOSITORY_ROOT / "deploy" / "compose.yaml").read_text())
    services = compose["services"]

    assert services["mongo"]["image"] == "mongo:7.0.39-jammy"
    assert services["mongo"]["mem_limit"] == "352m"
    assert services["web"]["mem_limit"] == "96m"
    assert services["caddy"]["mem_limit"] == "32m"
    assert sum(service["cpus"] for service in services.values()) == 1.0
    assert "0.256" in services["mongo"]["command"]
    assert "100" in services["mongo"]["command"]
    for service in services.values():
        assert service["logging"]["options"] == {"max-size": "5m", "max-file": "2"}


def test_fastapi_publish_workflow_scope_and_tags() -> None:
    workflow = (
        REPOSITORY_ROOT / ".github" / "workflows" / "publish-fastapi.yml"
    ).read_text()

    assert 'branches:' in workflow
    assert '- main' in workflow
    assert '"fastapi/**"' in workflow
    assert '"!fastapi/**/*.md"' in workflow
    assert "android" not in workflow
    assert "pytest -q" in workflow
    assert "platforms: linux/amd64" in workflow
    assert "packages: write" in workflow
    assert "${{ github.sha }}" in workflow
    assert ":latest" in workflow
    assert "imagetools inspect" in workflow
    assert "docker/build-push-action@v6" in workflow
    assert "retention" not in workflow.lower()


def test_manual_deployment_pulls_an_immutable_ghcr_image() -> None:
    compose = yaml.safe_load((REPOSITORY_ROOT / "deploy" / "compose.yaml").read_text())
    web = compose["services"]["web"]
    script = (REPOSITORY_ROOT / "deploy" / "deploy_image.sh").read_text()

    assert web["image"].startswith("ghcr.io/vesapehkonen/jatrail:")
    assert "JATRAIL_IMAGE_VERSION" in web["image"]
    assert "build" not in web
    assert "latest|[0-9a-f]{40}" in script
    assert "pull mongo caddy" in script
    assert "pull web" in script
    assert "--no-build --pull never --wait" in script
    assert 'curl --fail' in script
    assert "secrets/mongo_app_password" in script
    assert "docker image" not in script
    assert "rollback" in script.lower()
