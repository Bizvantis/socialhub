#!/usr/bin/env bash
# One-time setup of an Ubuntu 22.04/24.04 VM (Azure, Oracle Cloud, ...; x64 or arm64) for the
# SocialHub public demo. Safe to re-run. Run from the project directory (/opt/socialhub):
#
#   bash infra/vm/setup-server.sh <site-domain> <media-domain> --agree-le-tos
#
# Azure:  site  = <label>.<region>.cloudapp.azure.com   (DNS name label on the VM's public IP)
#         media = media.<ip-with-dashes>.sslip.io      (cloudapp names have no subdomains)
# Oracle: site  = <ip-with-dashes>.sslip.io, media = media.<ip-with-dashes>.sslip.io
# (sslip.io resolves any name containing <ip-with-dashes> to that IP: no DNS setup needed.)
set -euo pipefail

SITE="${1:?usage: setup-server.sh <site-domain> <media-domain> --agree-le-tos}"
MEDIA="${2:?usage: setup-server.sh <site-domain> <media-domain> --agree-le-tos}"
AGREE="${3:-}"
APP="$(pwd)"
export DEBIAN_FRONTEND=noninteractive

echo "== packages"
if ! command -v docker >/dev/null; then
  sudo apt-get update -y
  sudo apt-get install -y ca-certificates curl gnupg
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo usermod -aG docker "$USER"
fi
command -v certbot >/dev/null || sudo apt-get install -y certbot python3

echo "== firewall"
# Oracle's Ubuntu image rejects everything but SSH in iptables; Azure/AWS filter in the cloud
# firewall (NSG / security group) instead, so only touch iptables if a REJECT rule exists.
if sudo iptables -S INPUT | grep -q -- '-j REJECT'; then
  for port in 80 443; do
    sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
      || sudo iptables -I INPUT 1 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
  done
  command -v netfilter-persistent >/dev/null || sudo apt-get install -y iptables-persistent
  sudo netfilter-persistent save >/dev/null
fi

echo "== swap and kernel settings"
if [ "$(swapon --show | wc -l)" -eq 0 ]; then
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi
printf 'vm.max_map_count=262144\nvm.swappiness=10\n' | sudo tee /etc/sysctl.d/99-socialhub.conf >/dev/null
sudo sysctl -q --system

echo "== secrets (.env, generated once, never committed)"
if [ ! -f .env ]; then
  gen() { python3 -c "import secrets; print(secrets.token_urlsafe($1))"; }
  cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$(gen 24)
REDIS_PASSWORD=$(gen 24)
JWT_SECRET=$(gen 48)
RABBITMQ_PASSWORD=$(gen 18)
GRAFANA_PASSWORD=$(gen 18)
DEMO_PASSWORD=$(gen 12)
EOF
fi
# Site- and size-specific settings, rewritten on every run.
RAM_GB=$(awk '/MemTotal/{printf "%d", $2/1024/1024 + 0.5}' /proc/meminfo)
if [ "$RAM_GB" -ge 16 ]; then JVM=768m; ES=1g; WORKER=1536m; else JVM=420m; ES=512m; WORKER=1g; fi
sed -i '/^SITE_DOMAIN=\|^MEDIA_DOMAIN=\|^S3_PUBLIC_ENDPOINT=\|^S3_CORS_ORIGINS=\|^WEB_BASE_URL=\|^PUBLIC_ORIGIN=\|^JVM_MEM_LIMIT=\|^ES_HEAP=\|^WORKER_MEM_LIMIT=/d' .env
cat >> .env <<EOF
SITE_DOMAIN=$SITE
MEDIA_DOMAIN=$MEDIA
S3_PUBLIC_ENDPOINT=https://$MEDIA
S3_CORS_ORIGINS=https://$SITE
WEB_BASE_URL=https://$SITE/app
PUBLIC_ORIGIN=https://$SITE
JVM_MEM_LIMIT=$JVM
ES_HEAP=$ES
WORKER_MEM_LIMIT=$WORKER
EOF
chmod 600 .env

echo "== TLS certificate for $SITE and $MEDIA"
if [ ! -f "/etc/letsencrypt/live/$SITE/fullchain.pem" ]; then
  [ "$AGREE" = "--agree-le-tos" ] || { echo "Re-run with --agree-le-tos to accept the Let's Encrypt subscriber agreement."; exit 1; }
  sudo docker stop socialhub-web 2>/dev/null || true
  sudo certbot certonly --standalone --non-interactive --agree-tos --register-unsafely-without-email \
    --cert-name "$SITE" -d "$SITE" -d "$MEDIA"
fi
sudo mkdir -p /var/www/certbot /etc/letsencrypt/renewal-hooks/pre /etc/letsencrypt/renewal-hooks/post
printf '#!/bin/sh\ndocker stop socialhub-web\n'  | sudo tee /etc/letsencrypt/renewal-hooks/pre/stop-web.sh >/dev/null
printf '#!/bin/sh\ndocker start socialhub-web\n' | sudo tee /etc/letsencrypt/renewal-hooks/post/start-web.sh >/dev/null
sudo chmod +x /etc/letsencrypt/renewal-hooks/pre/stop-web.sh /etc/letsencrypt/renewal-hooks/post/start-web.sh

echo "== backend jars (built on the VM in a throwaway Maven container; tests already ran locally/CI)"
if [ "$(ls backend/*/target/*-0.0.1-SNAPSHOT.jar 2>/dev/null | wc -l)" -lt 14 ]; then
  sudo docker run --rm -v "$APP/backend":/src -v socialhub-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-21 mvn -B -q -DskipTests package
fi

echo "== build and start ($(nproc) CPUs, ${RAM_GB} GB RAM: JVM limit $JVM, ES heap $ES)"
COMPOSE="sudo docker compose --env-file .env -f docker-compose.yml -f docker-compose.full.yml -f docker-compose.vm.yml"
$COMPOSE build
$COMPOSE up -d
echo "waiting for the gateway..."
for i in $(seq 1 120); do
  [ "$(sudo docker inspect -f '{{.State.Health.Status}}' socialhub-api-gateway-1 2>/dev/null)" = healthy ] && break
  sleep 5
done
$COMPOSE ps --format 'table {{.Name}}\t{{.Status}}'

echo "== daily story refresh (stories expire after 24 h)"
CRON="17 6 * * * cd $APP/tools/seed && SEED_GUEST_PASSWORD=\$(grep ^DEMO_PASSWORD= $APP/.env | cut -d= -f2) python3 seed_demo.py --base https://$SITE --stories-only >> $APP/stories.log 2>&1"
{ crontab -l 2>/dev/null | grep -v 'seed_demo.py' || true; echo "$CRON"; } | crontab -   # no crontab yet is fine

echo "done: https://$SITE"
