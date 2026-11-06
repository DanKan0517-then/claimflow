# Runbook

All commands run from the repo folder on the server. `C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"`

## Health at a glance
```bash
$C ps                                   # every service should be "healthy"
curl -s https://<domain>/nginx-health
$C exec api-1 wget -qO- localhost:8080/actuator/health
```
Grafana (via SSH tunnel) has the **ClaimFlow – API overview** dashboard: request rate per replica, p95 latency, 5xx rate, queue depth, AI fallbacks, JVM heap and DB connections.

## Logs
```bash
$C logs -f --tail=100 api-1 api-2       # app logs, each line has the request id
$C logs --tail=200 nginx                # JSON access logs
$C logs nginx | grep '"status":5'       # server errors
```
Find one request across nginx and the API: copy `X-Request-ID` from the browser response headers and `grep` for it.

## Common problems

| Symptom | Check | Fix |
|---|---|---|
| Claims stuck in "AI review" | `$C exec redis redis-cli -a $REDIS_PASSWORD llen claimflow:jobs:analysis` and API logs for `Analysis of claim` | They are re-queued automatically every minute. Dead jobs: `lrange claimflow:jobs:analysis:dead 0 -1` |
| AI summaries say `rules` instead of `groq` | Grafana panel "AI calls by provider" shows `fallback` | Check `GROQ_API_KEY`, Groq status/rate limits. The app keeps working either way |
| 502 from nginx | `$C ps` - is an API replica unhealthy? | `$C restart api-1`. nginx routes to the other replica meanwhile |
| Users get 429 | nginx logs show many requests from one IP | Expected for brute-force on login. Raise `API_RATE_LIMIT` in `.env` if real traffic is hitting it |
| "Updated by someone else" errors | normal optimistic-lock conflict | Nothing to fix; user refreshes |

## Rolling restart (no downtime)
```bash
$C up -d --no-deps --build api-1 && $C up -d --no-deps --wait api-1
$C up -d --no-deps --build api-2 && $C up -d --no-deps --wait api-2
```
nginx keeps serving from whichever replica is up.

## Backups and restore
Nightly at 02:45 via cron: `deploy/backups/<date>/claimflow.dump` (Postgres) plus `uploads.tgz`, kept for 14 days. Copy them off the server regularly (e.g. to OCI Object Storage).

Restore:
```bash
$C stop api-1 api-2
cat deploy/backups/<date>/claimflow.dump | $C exec -T postgres pg_restore -U claimflow -d claimflow --clean --if-exists
cat deploy/backups/<date>/uploads.tgz | $C exec -T api-1 tar xzf - -C /data   # start api-1 first if it's stopped
$C start api-1 api-2
```

## Rollback
```bash
git log --oneline -5
git checkout <previous-good-commit>
$C up -d --build --wait
```
Flyway migrations only ever add. Never edit an applied migration; write a new one.

## Rotating secrets
- `JWT_SECRET`: change it in `.env` and restart both APIs. Everyone is logged out.
- DB/Redis passwords: change them in the service and in `.env` together, then `$C up -d`.
