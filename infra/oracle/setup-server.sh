#!/usr/bin/env bash
# One-time setup of an Oracle Cloud Ubuntu 24.04 (arm64) VM for the SocialHub demo.
# Safe to re-run. Run as the default "ubuntu" user from the project directory (/opt/socialhub):
#   bash infra/oracle/setup-server.sh <site-domain> [--agree-le-tos]
# e.g. 140-238-10-20.sslip.io   (sslip.io resolves <ip-with-dashes>.sslip.io to that IP; no DNS setup)
set -euo pipefail

SITE="${1:?usage: setup-server.sh <site-domain> [--agree-le-tos]}"
AGREE="${2:-}"
APP=/opt/socialhub
cd "$APP"

echo "== packages"
if ! command -v docker >/dev/null; then
  sudo apt-get update -y
  sudo apt-get install -y ca-certificates curl gnupg
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update -y
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo usermod -aG docker "$USER"
fi
sudo apt-get install -y certbot python3 netfilter-persistent >/dev/null

echo "== firewall (Oracle's Ubuntu image rejects everything but SSH in iptables)"
for port in 80 443; do
  sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || sudo iptables -I INPUT 5 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
sudo netfilter-persistent save >/dev/null

echo "== kernel setting for Elasticsearch"
echo "vm.max_map_count=262144" | sudo tee /etc/sysctl.d/99-elasticsearch.conf >/dev/null
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
# Site-specific settings (rewritten on every run so the domain can change).
sed -i '/^SITE_DOMAIN=\|^S3_PUBLIC_ENDPOINT=\|^S3_CORS_ORIGINS=\|^WEB_BASE_URL=/d' .env
cat >> .env <<EOF
SITE_DOMAIN=$SITE
S3_PUBLIC_ENDPOINT=https://media.$SITE
S3_CORS_ORIGINS=https://$SITE
WEB_BASE_URL=https://$SITE/app
EOF
chmod 600 .env

echo "== TLS certificate for $SITE and media.$SITE"
if [ ! -f "/etc/letsencrypt/live/$SITE/fullchain.pem" ]; then
  [ "$AGREE" = "--agree-le-tos" ] || { echo "Re-run with --agree-le-tos to accept the Let's Encrypt subscriber agreement."; exit 1; }
  sudo docker stop socialhub-web 2>/dev/null || true
  sudo certbot certonly --standalone --non-interactive --agree-tos --register-unsafely-without-email \
    --cert-name "$SITE" -d "$SITE" -d "media.$SITE"
fi
sudo mkdir -p /var/www/certbot /etc/letsencrypt/renewal-hooks/pre /etc/letsencrypt/renewal-hooks/post
printf '#!/bin/sh\ndocker stop socialhub-web\n'  | sudo tee /etc/letsencrypt/renewal-hooks/pre/stop-web.sh >/dev/null
printf '#!/bin/sh\ndocker start socialhub-web\n' | sudo tee /etc/letsencrypt/renewal-hooks/post/start-web.sh >/dev/null
sudo chmod +x /etc/letsencrypt/renewal-hooks/pre/stop-web.sh /etc/letsencrypt/renewal-hooks/post/start-web.sh

echo "== build and start ($(nproc) CPUs, $(free -g | awk '/Mem/{print $2}') GB RAM)"
COMPOSE="sudo docker compose --env-file .env -f docker-compose.yml -f docker-compose.full.yml -f docker-compose.oracle.yml"
$COMPOSE build
$COMPOSE up -d
echo "waiting for the gateway..."
for i in $(seq 1 90); do
  [ "$(sudo docker inspect -f '{{.State.Health.Status}}' socialhub-api-gateway-1 2>/dev/null)" = healthy ] && break
  sleep 5
done
$COMPOSE ps --format 'table {{.Name}}\t{{.Status}}'

echo "== daily story refresh (stories expire after 24 h)"
CRON="17 6 * * * cd $APP/tools/seed && SEED_GUEST_PASSWORD=\$(grep ^DEMO_PASSWORD= $APP/.env | cut -d= -f2) python3 seed_demo.py --base https://$SITE --stories-only >> $APP/stories.log 2>&1"
( crontab -l 2>/dev/null | grep -v 'seed_demo.py' ; echo "$CRON" ) | crontab -

echo "done: https://$SITE"
