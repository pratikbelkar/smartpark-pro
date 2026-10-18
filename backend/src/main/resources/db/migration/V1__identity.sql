-- V1: identity foundation — users, roles, sessions, audit, notifications.
-- All timestamps are timestamptz (UTC). Money is always BIGINT paise (see V3).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "citext";

-- ---------------------------------------------------------------- roles
CREATE TABLE roles (
    id          SMALLSERIAL PRIMARY KEY,
    role_name   VARCHAR(32) NOT NULL UNIQUE
);

INSERT INTO roles (role_name) VALUES
    ('SUPER_ADMIN'),
    ('PARKING_OWNER'),
    ('PARKING_STAFF'),
    ('CUSTOMER');

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(120) NOT NULL,
    email           CITEXT NOT NULL UNIQUE,
    phone           VARCHAR(20),
    password_hash   VARCHAR(255) NOT NULL,
    account_status  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_status ON users (account_status);

CREATE TABLE user_roles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     SMALLINT NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id)
);
CREATE INDEX idx_user_roles_user ON user_roles (user_id);

-- ------------------------------------------------------- refresh tokens
-- Opaque, hashed, single-use rotation (see AuthService).
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ------------------------------------------------------------ audit log
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(64) NOT NULL,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);

-- ---------------------------------------------------------- notifications
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title               VARCHAR(160) NOT NULL,
    message             TEXT NOT NULL,
    notification_type   VARCHAR(48) NOT NULL,
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
