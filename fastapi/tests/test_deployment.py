import hashlib
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
    assert "health_uri" not in caddyfile
    assert "request_body" in caddyfile
    assert "JATRAIL_MAX_REQUEST_BODY:150MB" in caddyfile

    dockerfile = (REPOSITORY_ROOT / "fastapi" / "Dockerfile").read_text()
    assert "http://localhost:8000/health" in dockerfile
    assert "--interval=10s" in dockerfile
    assert "--timeout=2s" in dockerfile
    assert "--start-period=5s" in dockerfile
    assert "--retries=2" in dockerfile


def test_mongodb_backup_is_scoped_and_uses_existing_secrets() -> None:
    script = (REPOSITORY_ROOT / "deploy" / "scripts" / "backup_mongodb.sh").read_text()

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
    assert "healthcheck" not in services["mongo"]
    assert services["web"]["depends_on"]["mongo"]["condition"] == "service_started"
    assert services["mongo"]["mem_limit"] == "352m"
    assert services["web"]["mem_limit"] == "96m"
    assert services["caddy"]["mem_limit"] == "32m"
    assert sum(service["cpus"] for service in services.values()) == 1.0
    assert "0.256" in services["mongo"]["command"]
    assert "100" in services["mongo"]["command"]
    for service in services.values():
        assert service["logging"]["options"] == {"max-size": "5m", "max-file": "2"}


def test_fastapi_publish_workflow_scope_and_tags() -> None:
    workflow_path = REPOSITORY_ROOT / ".github" / "workflows" / "publish-fastapi.yml"
    workflow_bytes = workflow_path.read_bytes()
    workflow = workflow_bytes.decode()

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
    assert hashlib.sha256(workflow_bytes).hexdigest() == (
        "7d54a9285b5426e6eb9e6ee13857efda8f3fe5b34028edef2798cf302b3bf804"
    )


def test_stack_and_backend_deployments_have_separate_scope() -> None:
    compose = yaml.safe_load((REPOSITORY_ROOT / "deploy" / "compose.yaml").read_text())
    web = compose["services"]["web"]
    scripts = REPOSITORY_ROOT / "deploy" / "scripts"
    stack_script = (scripts / "deploy_stack.sh").read_text()
    backend_script = (scripts / "deploy_backend.sh").read_text()
    common_script = (scripts / "common.sh").read_text()

    assert web["image"].startswith("ghcr.io/vesapehkonen/jatrail:")
    assert "JATRAIL_IMAGE_VERSION" in web["image"]
    assert "build" not in web
    assert "pull mongo caddy web" in stack_script
    assert "Checking MongoDB once" in stack_script
    assert stack_script.count("mongosh --quiet") == 1
    assert "mongod_wait_attempts=30" in stack_script
    assert '"mongod"' in stack_script
    assert "01-create-app-user.sh" in stack_script
    assert "--wait web caddy" in stack_script

    assert 'docker pull "ghcr.io/vesapehkonen/jatrail:$image_tag"' in backend_script
    assert "compose pull" not in backend_script
    assert "--no-deps" in backend_script
    assert "mongo" not in backend_script.lower()
    assert "caddy" not in backend_script.lower()
    assert "compose up" in backend_script

    assert "latest|[0-9a-f]{40}" in common_script
    assert 'curl --fail' in common_script
    assert "health_attempts=12" in common_script
    assert "health_retry_seconds=5" in common_script
    assert "--max-time 5" in common_script
    assert "docker logs --tail=200 jatrail-caddy-1" in common_script

    init_script = (
        REPOSITORY_ROOT / "deploy" / "mongo-init" / "01-create-app-user.sh"
    ).read_text()
    assert "db.updateUser" in init_script
    assert "/run/secrets/mongo_root_username" in init_script
    assert "/run/secrets/mongo_root_password" in init_script


def test_manual_workflows_do_not_clone_source_onto_vps() -> None:
    workflows = REPOSITORY_ROOT / ".github" / "workflows"
    bootstrap = (workflows / "bootstrap-vps.yml").read_text()
    stack = (workflows / "deploy-stack.yml").read_text()
    backend = (workflows / "deploy-backend.yml").read_text()

    for workflow in (bootstrap, stack, backend):
        assert "workflow_dispatch" in workflow
        assert "environment: production" in workflow
        assert "VPS_SSH_PRIVATE_KEY" in workflow
        assert "VPS_SSH_HOST_KEY" in workflow
        assert "git clone" not in workflow
        assert "git pull" not in workflow

    assert "bootstrap_vps.sh" in bootstrap
    assert "compose.yaml" not in bootstrap

    assert "Create allowlisted deployment bundle" in stack
    assert "deploy/compose.yaml" in stack
    assert "deploy/Caddyfile" in stack
    assert "deploy/scripts/install_bundle.sh" in stack
    assert "deploy/examples" not in stack
    assert "fastapi/" not in stack
    assert "android/" not in stack

    assert "actions/checkout" not in backend
    assert "scp " not in backend
    assert "/srv/jatrail/deploy/scripts/deploy_backend.sh" in backend


def test_bootstrap_and_bundle_installer_preserve_runtime_secrets() -> None:
    scripts = REPOSITORY_ROOT / "deploy" / "scripts"
    bootstrap = (scripts / "bootstrap_vps.sh").read_text()
    installer = (scripts / "install_bundle.sh").read_text()

    assert "docker-ce" in bootstrap
    assert "docker-compose-plugin" in bootstrap
    assert "/srv/jatrail/deploy/secrets" in bootstrap
    assert "git clone" not in bootstrap
    assert "docker compose up" not in bootstrap

    assert "jatrail.env" not in installer
    assert "caddy.env" not in installer
    assert "image.env" not in installer
    assert '"$target_dir/secrets' not in installer
    assert "deploy_image.sh" in installer
