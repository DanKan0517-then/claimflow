# ADR-003: Redis for the job queue, WebSocket fan-out and caching

**Status:** accepted

## Context
We need three things that a single Postgres doesn't give us cheaply:
1. A work queue, so the slow AI analysis doesn't block the HTTP request.
2. Cross-replica broadcasting, so a browser connected to api-2 hears about a change made on api-1.
3. A shared cache for expensive dashboard aggregates.

Kafka or RabbitMQ would also work for (1) and (2), but they'd be a second piece of infrastructure to run and monitor on a small VM.

## Decision
Use Redis for all three:
- **Queue:** a list with `LPUSH`/`RPOP`. Jobs are enqueued only after the DB transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`), so a worker never sees a claim that doesn't exist yet. There are 3 retries, then a dead-letter list.
- **Pub/sub:** channel `claimflow:claim-events`. Every replica subscribes and forwards events to its own WebSocket sessions, filtered by permission.
- **Cache:** `@Cacheable("analytics")` with a 30s TTL, shared across replicas.

## Consequences
- ✅ One extra container covers all three needs.
- ❌ A plain Redis list isn't a durable log: a job popped by a replica that then crashes is lost. We accept that because the **sweeper** re-queues any claim stuck in `AI_REVIEW`, and the consumer is idempotent. The database is the source of truth and the queue is only a hint. Redis Streams with consumer groups would be the next step if we needed exactly-once tracking.
- ❌ Pub/sub is fire-and-forget. A client that's offline misses events, but it refetches when it reconnects, so nothing is lost.
