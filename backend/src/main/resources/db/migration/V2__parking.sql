-- V2: parking inventory — owners, locations, floors, zones, slots, pricing, vehicles.
-- Time-based availability is enforced in V3 (holds + bookings + exclusion
-- constraint). physical_occupancy_status is the sensor/observed state and MUST
-- NOT be used as booking availability (see docs/ARCHITECTURE.md).

-- ------------------------------------------------------------------ owners
CREATE TABLE parking_owners (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE RESTRICT,
    business_name       VARCHAR(160) NOT NULL,
    business_email      CITEXT,
    business_phone      VARCHAR(20),
    business_address    TEXT,
    verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                        CHECK (verification_status IN ('PENDING', 'APPROVED', 'SUSPENDED', 'REJECTED')),
    tax_details         JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- --------------------------------------------------------------- locations
CREATE TABLE parking_locations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID NOT NULL REFERENCES parking_owners (id) ON DELETE RESTRICT,
    parking_name        VARCHAR(160) NOT NULL,
    description         TEXT,
    full_address        TEXT NOT NULL,
    city                VARCHAR(80) NOT NULL,
    state               VARCHAR(80),
    postal_code         VARCHAR(16),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    entrance_latitude   DOUBLE PRECISION,
    entrance_longitude  DOUBLE PRECISION,
    total_capacity      INTEGER NOT NULL DEFAULT 0 CHECK (total_capacity >= 0),
    opening_time        TIME,
    closing_time        TIME,
    operational_status  VARCHAR(16) NOT NULL DEFAULT 'OPEN'
                        CHECK (operational_status IN ('OPEN', 'CLOSED', 'HIDDEN')),
    deleted_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_locations_owner ON parking_locations (owner_id);
CREATE INDEX idx_locations_city ON parking_locations (city);
CREATE INDEX idx_locations_geo ON parking_locations (latitude, longitude);

CREATE TABLE parking_images (
    id                    BIGSERIAL PRIMARY KEY,
    parking_location_id   UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    image_url             TEXT NOT NULL,
    sort_order            INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_images_location ON parking_images (parking_location_id, sort_order);

-- -------------------------------------------------------- floors / zones
CREATE TABLE parking_floors (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parking_location_id   UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    floor_name            VARCHAR(40) NOT NULL,
    floor_number          INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_floor_per_location UNIQUE (parking_location_id, floor_name)
);

CREATE TABLE parking_zones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    floor_id    UUID NOT NULL REFERENCES parking_floors (id) ON DELETE CASCADE,
    zone_name   VARCHAR(40) NOT NULL,
    CONSTRAINT uq_zone_per_floor UNIQUE (floor_id, zone_name)
);

-- ------------------------------------------------------------------ slots
CREATE TABLE parking_slots (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parking_location_id     UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    floor_id                UUID NOT NULL REFERENCES parking_floors (id) ON DELETE RESTRICT,
    zone_id                 UUID NOT NULL REFERENCES parking_zones (id) ON DELETE RESTRICT,
    slot_number             VARCHAR(24) NOT NULL,
    vehicle_type            VARCHAR(16) NOT NULL
                            CHECK (vehicle_type IN ('TWO_WHEELER', 'CAR')),
    physical_occupancy_status VARCHAR(16) NOT NULL DEFAULT 'FREE'
                            CHECK (physical_occupancy_status IN ('FREE', 'OCCUPIED')),
    operational_status      VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE'
                            CHECK (operational_status IN ('AVAILABLE', 'DISABLED', 'MAINTENANCE')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_slot_per_location UNIQUE (parking_location_id, slot_number)
);
CREATE INDEX idx_slots_location ON parking_slots (parking_location_id, vehicle_type, operational_status);
CREATE INDEX idx_slots_floor_zone ON parking_slots (floor_id, zone_id);

-- Slot maintenance windows (supporting table).
CREATE TABLE slot_maintenance (
    id          BIGSERIAL PRIMARY KEY,
    slot_id     UUID NOT NULL REFERENCES parking_slots (id) ON DELETE CASCADE,
    reason      TEXT NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ,
    created_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_maintenance_slot ON slot_maintenance (slot_id, starts_at);

-- ----------------------------------------------------------------- pricing
-- Rates stored as BIGINT paise. effective_until NULL = currently effective.
CREATE TABLE parking_pricing (
    id                      BIGSERIAL PRIMARY KEY,
    parking_location_id     UUID NOT NULL REFERENCES parking_locations (id) ON DELETE CASCADE,
    vehicle_type            VARCHAR(16) NOT NULL
                            CHECK (vehicle_type IN ('TWO_WHEELER', 'CAR')),
    pricing_type            VARCHAR(16) NOT NULL DEFAULT 'HOURLY'
                            CHECK (pricing_type IN ('HOURLY', 'DAILY', 'FLAT', 'WEEKEND', 'PEAK')),
    hourly_rate_paise       BIGINT NOT NULL CHECK (hourly_rate_paise >= 0),
    daily_rate_paise        BIGINT CHECK (daily_rate_paise IS NULL OR daily_rate_paise >= 0),
    grace_period_minutes    INTEGER NOT NULL DEFAULT 15 CHECK (grace_period_minutes >= 0),
    effective_from          TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_until         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pricing UNIQUE (parking_location_id, vehicle_type, pricing_type, effective_from)
);
CREATE INDEX idx_pricing_effective ON parking_pricing (parking_location_id, vehicle_type, effective_from);

-- ---------------------------------------------------------------- vehicles
CREATE TABLE vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vehicle_number  VARCHAR(20) NOT NULL,
    vehicle_type    VARCHAR(16) NOT NULL
                    CHECK (vehicle_type IN ('TWO_WHEELER', 'CAR')),
    vehicle_model   VARCHAR(80),
    vehicle_color   VARCHAR(40),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vehicle_per_user UNIQUE (user_id, vehicle_number)
);
CREATE INDEX idx_vehicles_user ON vehicles (user_id);
