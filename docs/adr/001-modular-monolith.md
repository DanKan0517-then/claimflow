# ADR-001: Modular monolith, scaled horizontally, instead of microservices

**Status:** accepted

## Context
The domain has clear modules (users, policies, claims, documents, AI, notifications, analytics). We could deploy each as a separate service. The team is small, the load is moderate (an insurer's claims team, not consumer scale), and most operations touch several modules in one transaction. For example, approving a claim updates the claim, writes the audit trail and creates notifications, and all of it must succeed or fail together.

## Decision
Ship one Spring Boot application with strict package boundaries, and run **two identical replicas** behind nginx. The slow, failure-prone part (AI analysis) is already decoupled through a Redis queue, so it can be split out later without changing the API.

## Consequences
- ✅ Real ACID transactions across modules. No sagas or distributed transactions for "approve + audit + notify".
- ✅ One build, one image, one deploy. Easier to run on a single free VM.
- ✅ Still horizontally scalable and fault tolerant (two replicas, stateless).
- ❌ Modules scale together. If AI analysis became heavy, we'd move the `ai` package into its own worker service consuming the same Redis queue (`AI_WORKER_ENABLED=false` on the API replicas already supports this).
