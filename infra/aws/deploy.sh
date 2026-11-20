#!/usr/bin/env bash
# Runs ON the EC2 host (called by Jenkins over SSH): pull the tagged images and roll the stack.
#   TAG=abc1234 REGISTRY=docker.io/socialhub ./infra/aws/deploy.sh
set -euo pipefail
cd /opt/socialhub
: "${TAG:?TAG is required}" "${REGISTRY:?REGISTRY is required}"

./infra/aws/fetch-secrets.sh
export TAG REGISTRY

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.full.yml -f docker-compose.prod.yml"
$COMPOSE pull --quiet
# Infrastructure first (no-op if unchanged), then services; compose waits on health checks.
$COMPOSE up -d --remove-orphans
$COMPOSE ps --format 'table {{.Name}}\t{{.Status}}'

# Keep the disk tidy: old images from previous releases.
docker image prune -af --filter "until=168h" > /dev/null || true
echo "Deployed ${TAG}"
