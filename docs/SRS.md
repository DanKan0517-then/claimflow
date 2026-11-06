# ClaimFlow – Software Requirements Specification

## 1. Purpose
ClaimFlow lets an insurer take claims online, check them quickly with AI help, and route them to the right people for a decision. It replaces email + spreadsheet claim handling, where claims get lost, take weeks, and fraud checks depend on whoever happens to look.

## 2. Users

| Role | Who | Main goal |
|---|---|---|
| Customer | Policy holder | File a claim easily and know where it stands |
| Adjuster | Claims staff | Clear the review queue fast without missing fraud |
| Manager | Senior claims staff | Sign off large/risky payouts, watch SLAs and team load |
| Admin | IT / operations | Manage accounts and policies |

## 3. Functional requirements

**Accounts**
- FR-1 Customers can register with email + password; staff accounts are created only by an admin.
- FR-2 Login returns a token valid for 12 hours; disabled accounts can't log in.
- FR-3 Admin can create users with any role and enable/disable them.

**Policies**
- FR-4 Admin/manager can issue a policy (type, sum insured, premium, start/end) to a customer.
- FR-5 Customers see only their own policies.

**Claims – customer**
- FR-6 A customer can create a claim against one of their active policies. The system rejects claims where the incident date is outside the policy period, the date is in the future, or the amount is above the sum insured.
- FR-7 A claim starts as a draft. The customer can attach up to 10 documents (PDF, PNG, JPG, TXT, max 10 MB each) and then submit.
- FR-8 After submission the customer sees progress (submitted → in review → decision → paid) and gets notified at each step.
- FR-9 When staff request more information, the customer can upload documents and reply, which sends the claim back for review.

**Claims – AI review**
- FR-10 Every submitted claim is analysed automatically: summary, bill total, provider and items extracted from documents.
- FR-11 Each claim gets a fraud score 0–100 with a list of human-readable reasons.
- FR-12 After analysis the claim is assigned to the adjuster with the fewest open claims.
- FR-13 If the LLM is unavailable the system must fall back to rule-based analysis; a claim must never stay stuck in AI review.

**Claims – staff**
- FR-14 Staff see a queue filterable by status, risk, assignment and text, sortable by SLA, risk, amount, date.
- FR-15 Adjusters can approve (full or partial amount), reject (reason required, min 10 chars), or request information.
- FR-16 An adjuster's approval above ₹50,000, or on a claim with fraud score ≥ 70, goes to "pending approval"; only a manager/admin can make the final decision (four-eyes principle).
- FR-17 Only managers/admins can mark an approved claim as paid.
- FR-18 Staff can leave internal notes that customers never see.
- FR-19 Every state change, upload and assignment is recorded in an audit trail with actor and time.
- FR-20 Dashboard shows open claims, SLA breaches, high-risk count, approval rate, average decision time, 14-day trend and workload per adjuster.

**Live updates**
- FR-21 Open screens update without refresh when a claim changes (WebSocket).

## 4. Non-functional requirements

| ID | Requirement | How it's met |
|---|---|---|
| NFR-1 Performance | p95 < 500 ms for list/detail APIs at 30 concurrent users | k6 load test in CI enforces thresholds |
| NFR-2 Availability | One API instance can fail without downtime | 2 replicas behind nginx with passive health checks and retry on the other upstream |
| NFR-3 Scalability | Add API instances without code changes | Stateless JWT auth, shared Postgres/Redis/volume, Redis pub/sub for WebSocket fan-out |
| NFR-4 Consistency | No lost updates when two people act on one claim | Optimistic locking (`@Version`) → HTTP 409 |
| NFR-5 Reliability | AI jobs survive crashes and Redis hiccups | After-commit enqueue, retries, dead-letter list, stuck-claim sweeper, idempotent consumer |
| NFR-6 Security | OWASP basics | BCrypt, signed JWT, RBAC, per-customer data isolation, input validation, upload magic-byte check, rate limiting, security headers/CSP, HTTPS + HSTS |
| NFR-7 Privacy | Customer PII not sent to the LLM | Only claim text, amounts and document text are sent; no names/emails/phones |
| NFR-8 Observability | Know when it's slow or failing | Prometheus metrics, Grafana dashboard, JSON access logs with request IDs |
| NFR-9 Maintainability | Safe to change | Flyway migrations, 30+ automated tests, CI on every push |
| NFR-10 SLA targets | Decision targets per claim type | Health 48h, Motor 72h, Travel 72h, Property 120h; breaches highlighted |

## 5. Out of scope (v1)
Payments integration (payout is marked manually), OCR for scanned images, email/SMS notifications, multi-insurer tenancy, policy purchase flow.
