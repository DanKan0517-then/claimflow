#!/usr/bin/env bash
# Nightly Postgres dump + uploaded documents, keeps the last 14 days.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
DIR="deploy/backups/$(date +%Y-%m-%d)"
mkdir -p "$DIR"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom > "$DIR/claimflow.dump"
docker compose exec -T api-1 tar czf - -C /data uploads > "$DIR/uploads.tgz"
find deploy/backups -maxdepth 1 -type d -mtime +14 -exec rm -rf {} +
echo "$(date -Is) backup ok: $(du -sh "$DIR" | cut -f1)"
