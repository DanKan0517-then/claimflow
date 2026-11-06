#!/usr/bin/env bash
# One-command production deploy on a fresh Ubuntu VM (e.g. Oracle Cloud Always Free).
#   ./deploy/deploy.sh claims.example.com you@example.com
# Safe to re-run: it pulls the latest code, rebuilds and restarts with zero manual steps.
set -euo pipefail
DOMAIN="${1:?usage: deploy.sh <domain> <email>}"
EMAIL="${2:?usage: deploy.sh <domain> <email>}"
cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

# 1. Docker
if ! command -v docker >/dev/null; then
  echo "Installing Docker..."
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
  echo "Docker installed. Log out and back in once, then run this script again."
  exit 0
fi

# 2. .env with strong random secrets on first run
if [ ! -f .env ]; then
  cp .env.example .env
  rand() { openssl rand -base64 "$1" | tr -d '/+=\n' | cut -c1-"$2"; }
  sed -i "s|^SERVER_NAME=.*|SERVER_NAME=$DOMAIN|" .env
  sed -i "s|^PUBLIC_URL=.*|PUBLIC_URL=https://$DOMAIN|" .env
  sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(rand 32 32)|" .env
  sed -i "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=$(rand 32 32)|" .env
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(rand 64 64)|" .env
  sed -i "s|^GRAFANA_PASSWORD=.*|GRAFANA_PASSWORD=$(rand 24 20)|" .env
  sed -i "s|^LETSENCRYPT_EMAIL=.*|LETSENCRYPT_EMAIL=$EMAIL|" .env
  echo "Created .env with random secrets (keep a copy somewhere safe)."
fi

# 3. TLS certificate (first run only). nginx isn't up yet, so certbot binds port 80 itself.
if [ ! -f "deploy/certbot/conf/live/$DOMAIN/fullchain.pem" ]; then
  echo "Requesting Let's Encrypt certificate for $DOMAIN..."
  mkdir -p deploy/certbot/conf deploy/certbot/www
  $COMPOSE stop nginx 2>/dev/null || true
  docker run --rm -p 80:80 \
    -v "$PWD/deploy/certbot/conf:/etc/letsencrypt" \
    -v "$PWD/deploy/certbot/www:/var/www/certbot" \
    certbot/certbot certonly --standalone -d "$DOMAIN" -m "$EMAIL" --agree-tos -n
fi

# 4. Build and (re)start. --wait blocks until every health check passes.
git pull --ff-only || true
$COMPOSE up -d --build --remove-orphans --wait --wait-timeout 300

# 5. Reload nginx so renewed certificates are picked up, and add a daily cron for it + backups
( crontab -l 2>/dev/null | grep -v claimflow ; \
  echo "17 3 * * * cd $PWD && $COMPOSE exec -T nginx nginx -s reload >/dev/null 2>&1 # claimflow" ; \
  echo "45 2 * * * cd $PWD && ./deploy/backup.sh >> deploy/backup.log 2>&1 # claimflow" ) | crontab -

echo
echo "Deployed: https://$DOMAIN"
./deploy/smoke-test.sh "https://$DOMAIN" || echo "Smoke test failed - check: $COMPOSE logs --tail=100"
