-- ClaimFlow initial schema

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER','ADJUSTER','MANAGER','ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE policies (
    id            BIGSERIAL PRIMARY KEY,
    policy_number VARCHAR(30)    NOT NULL UNIQUE,
    holder_id     BIGINT         NOT NULL REFERENCES users(id),
    type          VARCHAR(20)    NOT NULL CHECK (type IN ('HEALTH','MOTOR','PROPERTY','TRAVEL')),
    sum_insured   NUMERIC(14,2)  NOT NULL CHECK (sum_insured > 0),
    premium       NUMERIC(12,2)  NOT NULL CHECK (premium >= 0),
    start_date    DATE           NOT NULL,
    end_date      DATE           NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LAPSED','CANCELLED')),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT policy_dates CHECK (end_date > start_date)
);
CREATE INDEX idx_policies_holder ON policies(holder_id);

CREATE TABLE claims (
    id                  BIGSERIAL PRIMARY KEY,
    claim_number        VARCHAR(30)   NOT NULL UNIQUE,
    policy_id           BIGINT        NOT NULL REFERENCES policies(id),
    customer_id         BIGINT        NOT NULL REFERENCES users(id),
    assigned_adjuster_id BIGINT       REFERENCES users(id),
    type                VARCHAR(20)   NOT NULL,
    status              VARCHAR(30)   NOT NULL,
    title               VARCHAR(160)  NOT NULL,
    description         TEXT          NOT NULL,
    incident_date       DATE          NOT NULL,
    claimed_amount      NUMERIC(14,2) NOT NULL CHECK (claimed_amount > 0),
    approved_amount     NUMERIC(14,2),
    fraud_score         INTEGER,
    fraud_flags         TEXT,
    ai_summary          TEXT,
    ai_extracted        TEXT,
    ai_provider         VARCHAR(30),
    decision_reason     TEXT,
    sla_due_at          TIMESTAMPTZ   NOT NULL,
    decided_at          TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_claims_customer ON claims(customer_id);
CREATE INDEX idx_claims_status ON claims(status);
CREATE INDEX idx_claims_adjuster ON claims(assigned_adjuster_id);
CREATE INDEX idx_claims_policy ON claims(policy_id);
CREATE INDEX idx_claims_created ON claims(created_at);

CREATE TABLE claim_documents (
    id             BIGSERIAL PRIMARY KEY,
    claim_id       BIGINT       NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(120) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    sha256         VARCHAR(64)  NOT NULL,
    storage_key    VARCHAR(255) NOT NULL,
    extracted_text TEXT,
    uploaded_by    BIGINT       NOT NULL REFERENCES users(id),
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_claim ON claim_documents(claim_id);
CREATE INDEX idx_documents_sha ON claim_documents(sha256);

CREATE TABLE claim_comments (
    id         BIGSERIAL PRIMARY KEY,
    claim_id   BIGINT      NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users(id),
    body       TEXT        NOT NULL,
    internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_claim ON claim_comments(claim_id);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    claim_id    BIGINT      REFERENCES claims(id) ON DELETE CASCADE,
    actor_id    BIGINT      REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    details     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_claim ON audit_log(claim_id);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    claim_id   BIGINT       REFERENCES claims(id) ON DELETE CASCADE,
    message    VARCHAR(500) NOT NULL,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, read);

-- human readable claim numbers: CLM-2026-000123
CREATE SEQUENCE claim_number_seq START 1000;
CREATE SEQUENCE policy_number_seq START 5000;
