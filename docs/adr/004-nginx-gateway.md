# ADR-004: nginx as the single entry point

**Status:** accepted

## Decision
All traffic enters through nginx, which:
- terminates TLS (Let's Encrypt, auto-renewed by a certbot sidecar)
- serves the React build with long-lived caching for hashed assets and `no-cache` for `index.html`
- load-balances `/api` and `/ws` across the API replicas (`least_conn`, passive health checks, retry on the other replica for idempotent requests)
- applies per-IP rate limits: 10/min on login and register, 30/min on uploads, configurable on the rest
- adds security headers (CSP, X-Frame-Options, nosniff, HSTS)
- hides `/actuator` from the internet (Prometheus scrapes it on the internal network)
- generates a request ID that is forwarded to the API and appears in both nginx and Spring logs

## Why
- The React app and the API share one origin, so there's no CORS in production and the JWT never crosses origins.
- Protection (rate limits, TLS, headers) sits in one place in front of every replica, instead of being re-implemented in the app.
- nginx is cheap to run, well understood, and used in most production setups.
