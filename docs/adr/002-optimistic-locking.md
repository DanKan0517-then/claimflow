# ADR-002: Optimistic locking for claim decisions

**Status:** accepted

## Context
Two staff members can open the same claim. If both act at once (one approves, one rejects), the last write would silently win, and the audit trail would show two contradictory decisions.

## Decision
- `claims.version` is a JPA `@Version` column. Hibernate adds `WHERE version = ?` to every update and fails if another transaction got there first.
- The UI sends the `version` it rendered as `expectedVersion` with every action. The server rejects it with **409 Conflict** if the claim has changed since, even when the change was a harmless one like a re-assignment. The person is acting on stale information.
- The AI worker uses the same check before writing its results.

## Why not pessimistic locks (`SELECT ... FOR UPDATE`)?
Conflicts are rare, and a row lock would have to be held while a human reads the page, which isn't possible over HTTP anyway. Optimistic locking costs nothing when there's no conflict and gives the user a clear "someone else updated this" message when there is one.
