-- V3: booking engine — holds, bookings, payments, QR passes, sessions, money.
-- All money is BIGINT paise. All timestamps are timestamptz (UTC).
--
-- Concurrency strategy (documented in docs/ARCHITECTURE.md):
--   1. SELECT ... FOR UPDATE on the parking_slots row inside a transaction.
--   2. Application check: no ACTIVE hold + no CONFIRMED/CHECKED_IN booking
--      overlapping [start_time, end_time) for the slot.
--   3. Exclusion constraints below act as the database backstop so that even
--      two racing transactions cannot both commit an overlap.

CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- ---------------------------------------------------------- booking holds
-- Short-lived (default 10 min) inventory locks created before payment.
CREATE TABLE parking_booking_holds (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parking_location_id UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    parking_slot_id     UUID NOT NULL REFERENCES parking_slots (id) ON DELETE CASCADE,
    vehicle_id          UUID REFERENCES vehicles (id) ON DELETE SET NULL,
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ NOT NULL,
    hold_range          TSTZRANGE GENERATED ALWAYS AS (tstzrange(start_time, end_time)) STORED,
    expires_at          TIMESTAMPTZ NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_hold_interval CHECK (end_time > start_time),
    CONSTRAINT chk_hold_expiry CHECK (expires_at > created_at),
    CONSTRAINT no_overlapping_holds EXCLUDE USING gist
        (parking_slot_id WITH =, hold_range WITH &&)
        WHERE (status = 'ACTIVE')
);
CREATE INDEX idx_holds_customer ON parking_booking_holds (customer_id);
CREATE INDEX idx_holds_expiry ON parking_booking_holds (expires_at) WHERE (status = 'ACTIVE');

-- --------------------------------------------------------------- bookings
CREATE TABLE parking_bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_reference   VARCHAR(24) NOT NULL UNIQUE,
    customer_id         UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    parking_location_id UUID NOT NULL REFERENCES parking_locations (id) ON DELETE RESTRICT,
    parking_slot_id     UUID NOT NULL REFERENCES parking_slots (id) ON DELETE RESTRICT,
    vehicle_id          UUID REFERENCES vehicles (id) ON DELETE SET NULL,
    hold_id             UUID REFERENCES parking_booking_holds (id) ON DELETE SET NULL,
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ NOT NULL,
    reserved_range      TSTZRANGE GENERATED ALWAYS AS (tstzrange(start_time, end_time)) STORED,
    actual_entry_time   TIMESTAMPTZ,
    actual_exit_time    TIMESTAMPTZ,
    parking_amount_paise    BIGINT NOT NULL CHECK (parking_amount_paise >= 0),
    convenience_fee_paise   BIGINT NOT NULL DEFAULT 0 CHECK (convenience_fee_paise >= 0),
    applicable_tax_paise    BIGINT NOT NULL DEFAULT 0 CHECK (applicable_tax_paise >= 0),
    total_amount_paise      BIGINT NOT NULL CHECK (total_amount_paise >= 0),
    -- Price snapshot: protects confirmed bookings from later price changes.
    pricing_snapshot    JSONB NOT NULL DEFAULT '{}'::jsonb,
    payment_status      VARCHAR(24) NOT NULL DEFAULT 'CREATED'
                        CHECK (payment_status IN ('CREATED', 'PENDING', 'AUTHORIZED', 'CAPTURED',
                                                  'FAILED', 'REFUND_PENDING', 'REFUNDED', 'PARTIALLY_REFUNDED')),
    booking_status      VARCHAR(16) NOT NULL DEFAULT 'PENDING_PAYMENT'
                        CHECK (booking_status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN',
                                                  'COMPLETED', 'CANCELLED', 'EXPIRED', 'NO_SHOW')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_interval CHECK (end_time > start_time),
    CONSTRAINT no_overlapping_bookings EXCLUDE USING gist
        (parking_slot_id WITH =, reserved_range WITH &&)
        WHERE (booking_status IN ('CONFIRMED', 'CHECKED_IN'))
);
CREATE INDEX idx_bookings_customer ON parking_bookings (customer_id, created_at DESC);
CREATE INDEX idx_bookings_slot ON parking_bookings (parking_slot_id, start_time);
CREATE INDEX idx_bookings_location ON parking_bookings (parking_location_id, start_time);
CREATE INDEX idx_bookings_status ON parking_bookings (booking_status);

-- --------------------------------------------------------------- payments
CREATE TABLE payments (
    id                          BIGSERIAL PRIMARY KEY,
    booking_id                  UUID NOT NULL REFERENCES parking_bookings (id) ON DELETE RESTRICT,
    razorpay_order_id           VARCHAR(64) NOT NULL UNIQUE,
    razorpay_payment_id         VARCHAR(64) UNIQUE,
    payment_amount_paise        BIGINT NOT NULL CHECK (payment_amount_paise >= 0),
    currency                    VARCHAR(8) NOT NULL DEFAULT 'INR',
    payment_method              VARCHAR(32),
    payment_status              VARCHAR(24) NOT NULL DEFAULT 'CREATED'
                                CHECK (payment_status IN ('CREATED', 'PENDING', 'AUTHORIZED', 'CAPTURED',
                                                          'FAILED', 'REFUND_PENDING', 'REFUNDED', 'PARTIALLY_REFUNDED')),
    gateway_response_reference  JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_booking ON payments (booking_id);

-- Idempotency guard: one CAPTURED payment settles a booking exactly once.
CREATE UNIQUE INDEX uq_payments_booking_captured
    ON payments (booking_id)
    WHERE (payment_status = 'CAPTURED');

-- --------------------------------------------------------------- refunds
CREATE TABLE refunds (
    id                  BIGSERIAL PRIMARY KEY,
    payment_id          BIGINT NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    booking_id          UUID NOT NULL REFERENCES parking_bookings (id) ON DELETE RESTRICT,
    amount_paise        BIGINT NOT NULL CHECK (amount_paise > 0),
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    razorpay_refund_id  VARCHAR(64) UNIQUE,
    reason              TEXT,
    processed_by        UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_booking ON refunds (booking_id);

-- ------------------------------------------------- webhook event log
-- gateway_event_id UNIQUE => webhook replay safety (re-delivery = no-op).
CREATE TABLE payment_webhook_events (
    id                  BIGSERIAL PRIMARY KEY,
    gateway_event_id    VARCHAR(128) NOT NULL UNIQUE,
    event_type          VARCHAR(64) NOT NULL,
    payload             JSONB NOT NULL DEFAULT '{}'::jsonb,
    processing_status   VARCHAR(16) NOT NULL DEFAULT 'RECEIVED'
                        CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')),
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ
);

-- -------------------------------------------------------------- qr passes
-- Only a SHA-256 hash of the token is stored; the token itself is opaque
-- and verified server-side on every scan.
CREATE TABLE qr_passes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL UNIQUE REFERENCES parking_bookings (id) ON DELETE CASCADE,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ
);

-- ---------------------------------------------------------------- sessions
CREATE TABLE parking_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL UNIQUE REFERENCES parking_bookings (id) ON DELETE RESTRICT,
    parking_slot_id     UUID NOT NULL REFERENCES parking_slots (id) ON DELETE RESTRICT,
    entry_time          TIMESTAMPTZ NOT NULL,
    exit_time           TIMESTAMPTZ,
    duration_minutes    INTEGER CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
    extra_amount_paise  BIGINT NOT NULL DEFAULT 0 CHECK (extra_amount_paise >= 0),
    session_status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (session_status IN ('ACTIVE', 'COMPLETED'))
);

CREATE TABLE entry_exit_logs (
    id              BIGSERIAL PRIMARY KEY,
    session_id      UUID REFERENCES parking_sessions (id) ON DELETE SET NULL,
    booking_id      UUID REFERENCES parking_bookings (id) ON DELETE SET NULL,
    staff_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    event_type      VARCHAR(16) NOT NULL
                    CHECK (event_type IN ('ENTRY', 'EXIT')),
    event_time      TIMESTAMPTZ NOT NULL DEFAULT now(),
    remarks         TEXT
);
CREATE INDEX idx_entry_exit_booking ON entry_exit_logs (booking_id);

-- ----------------------------------------------------------------- reviews
CREATE TABLE reviews (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parking_location_id UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    booking_id          UUID NOT NULL UNIQUE REFERENCES parking_bookings (id) ON DELETE CASCADE,
    rating              SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_location ON reviews (parking_location_id, created_at DESC);

-- ------------------------------------------- owner ledger + settlements
-- Every money movement for an owner is an immutable ledger row; settlement
-- status is derived, and Razorpay Route (or manual bank transfer) is only an
-- integration point recorded on the settlement row.
CREATE TABLE owner_ledger_entries (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    UUID NOT NULL REFERENCES parking_owners (id) ON DELETE RESTRICT,
    booking_id  UUID REFERENCES parking_bookings (id) ON DELETE SET NULL,
    entry_type  VARCHAR(32) NOT NULL
                CHECK (entry_type IN ('BOOKING_CREDIT', 'COMMISSION_DEBIT', 'REFUND_DEBIT',
                                      'ADJUSTMENT_CREDIT', 'ADJUSTMENT_DEBIT', 'SETTLEMENT_DEBIT')),
    amount_paise BIGINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_owner ON owner_ledger_entries (owner_id, created_at DESC);

CREATE TABLE owner_settlements (
    id                  BIGSERIAL PRIMARY KEY,
    owner_id            UUID NOT NULL REFERENCES parking_owners (id) ON DELETE RESTRICT,
    settlement_reference VARCHAR(48) NOT NULL UNIQUE,
    amount_paise        BIGINT NOT NULL CHECK (amount_paise >= 0),
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'SETTLED', 'FAILED')),
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_settlements_owner ON owner_settlements (owner_id, created_at DESC);

-- --------------------------------------------- platform configuration
-- Convenience fees, commission %, taxes: versioned rows, never updated in
-- place, so historical bookings keep the values snapshotted at quote time.
CREATE TABLE platform_fees (
    id              BIGSERIAL PRIMARY KEY,
    fee_key         VARCHAR(64) NOT NULL,
    value_paise     BIGINT,
    value_percent   NUMERIC(5,2),
    effective_from  TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_until TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fees_key ON platform_fees (fee_key, effective_from DESC);

INSERT INTO platform_fees (fee_key, value_paise) VALUES ('convenience_fee_paise', 1000);
INSERT INTO platform_fees (fee_key, value_percent) VALUES ('commission_percent', 15.00);

CREATE TABLE cancellation_policies (
    id                      BIGSERIAL PRIMARY KEY,
    parking_location_id     UUID REFERENCES parking_locations (id) ON DELETE CASCADE,
    hours_before_start      INTEGER NOT NULL CHECK (hours_before_start >= 0),
    refund_percent          NUMERIC(5,2) NOT NULL CHECK (refund_percent BETWEEN 0 AND 100),
    CONSTRAINT uq_policy UNIQUE (parking_location_id, hours_before_start)
);

-- Default platform policy: full refund >24h, 50% 4–24h, none <4h.
INSERT INTO cancellation_policies (parking_location_id, hours_before_start, refund_percent) VALUES
    (NULL, 24, 100.00),
    (NULL, 4, 50.00),
    (NULL, 0, 0.00);

-- --------------------------------------------- subscriptions (supporting)
CREATE TABLE subscription_plans (
    id              SMALLSERIAL PRIMARY KEY,
    plan_code       VARCHAR(24) NOT NULL UNIQUE,
    display_name    VARCHAR(80) NOT NULL,
    max_locations   INTEGER NOT NULL DEFAULT 1,
    capabilities    JSONB NOT NULL DEFAULT '{}'::jsonb
);

INSERT INTO subscription_plans (plan_code, display_name, max_locations, capabilities) VALUES
    ('FREE', 'Free', 1, '{"dashboard": "basic"}'),
    ('STARTER', 'Starter', 3, '{"dashboard": "basic", "reports": true}'),
    ('PROFESSIONAL', 'Professional', 10, '{"dashboard": "advanced", "reports": true, "staff": true, "support": "premium"}'),
    ('ENTERPRISE', 'Enterprise', 100, '{"dashboard": "advanced", "reports": "advanced", "staff": true, "support": "dedicated", "locations": "multiple"}');

CREATE TABLE owner_subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    UUID NOT NULL REFERENCES parking_owners (id) ON DELETE CASCADE,
    plan_id     SMALLINT NOT NULL REFERENCES subscription_plans (id),
    starts_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- --------------------------------------------- staff shifts (supporting)
CREATE TABLE staff_shifts (
    id                  BIGSERIAL PRIMARY KEY,
    staff_user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parking_location_id UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_shift_interval CHECK (ends_at > starts_at)
);

-- --------------------------------------------- support tickets (supporting)
CREATE TABLE support_tickets (
    id                  BIGSERIAL PRIMARY KEY,
    requester_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    subject             VARCHAR(200) NOT NULL,
    body                TEXT NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
