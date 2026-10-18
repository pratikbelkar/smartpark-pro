-- V4: development seed. DEV-ONLY credentials (Password123! for every seed user).
-- Never use these values outside local development.

-- ------------------------------------------------------------------ users
-- bcrypt('Password123!')
INSERT INTO users (id, full_name, email, phone, password_hash, email_verified) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Rajesh Sharma', 'owner1@dev.local', '9876500001',
     '$2a$10$/vnspwmoxdcQtc2StK9NbO/gvHgSRdMgnO7BCtC4C8P9WWabaDlpC', true),
    ('22222222-2222-2222-2222-222222222222', 'Sneha Patil', 'owner2@dev.local', '9876500002',
     '$2a$10$/vnspwmoxdcQtc2StK9NbO/gvHgSRdMgnO7BCtC4C8P9WWabaDlpC', true),
    ('33333333-3333-3333-3333-333333333333', 'Amit Gate', 'staff1@dev.local', '9876500003',
     '$2a$10$/vnspwmoxdcQtc2StK9NbO/gvHgSRdMgnO7BCtC4C8P9WWabaDlpC', true),
    ('44444444-4444-4444-4444-444444444444', 'Kiran Entry', 'staff2@dev.local', '9876500004',
     '$2a$10$/vnspwmoxdcQtc2StK9NbO/gvHgSRdMgnO7BCtC4C8P9WWabaDlpC', true),
    ('55555555-5555-5555-5555-555555555555', 'Rahul Verma', 'rahul@dev.local', '9876500005',
     '$2a$10$/vnspwmoxdcQtc2StK9NbO/gvHgSRdMgnO7BCtC4C8P9WWabaDlpC', true),
    ('66666666-6666-6666-6666-666666666666', 'Priya Nair', 'priya@dev.local', '9876500006',
     '$2a$10$/vnspwmoxdcQtc2StK9NbO/gvHgSRdMgnO7BCtC4C8P9WWabaDlpC', true);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.role_name = 'PARKING_OWNER'
WHERE u.email IN ('owner1@dev.local', 'owner2@dev.local');
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.role_name = 'CUSTOMER'
WHERE u.email IN ('owner1@dev.local', 'owner2@dev.local', 'rahul@dev.local', 'priya@dev.local');
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.role_name = 'PARKING_STAFF'
WHERE u.email IN ('staff1@dev.local', 'staff2@dev.local');

-- ------------------------------------------------------------------ owners
INSERT INTO parking_owners (id, user_id, business_name, business_email, business_phone,
                            business_address, verification_status) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111',
     'Sharma Parking Co', 'hello@sharmaparking.example', '020-40001001', 'MG Road, Camp, Pune', 'APPROVED'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222',
     'Patil Parking Services', 'hello@patilparking.example', '020-40001002', 'Baner Road, Pune', 'APPROVED');

INSERT INTO owner_subscriptions (owner_id, plan_id, starts_at)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', id, now() FROM subscription_plans WHERE plan_code = 'STARTER';
INSERT INTO owner_subscriptions (owner_id, plan_id, starts_at)
SELECT 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', id, now() FROM subscription_plans WHERE plan_code = 'PROFESSIONAL';

-- --------------------------------------------------------------- locations
INSERT INTO parking_locations (id, owner_id, parking_name, description, full_address, city, state,
                               postal_code, latitude, longitude, entrance_latitude, entrance_longitude,
                               total_capacity, opening_time, closing_time, operational_status) VALUES
    ('10000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'City Centre Parking', 'Covered multi-level parking in the heart of Camp.',
     'Moledina Road, Camp', 'Pune', 'Maharashtra', '411001',
     18.5196, 73.8728, 18.5199, 73.8725, 60, '06:00', '23:00', 'OPEN'),
    ('10000000-0000-0000-0000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'Phoenix Parking', 'Open-deck parking next to the mall.',
     'Viman Nagar Road', 'Pune', 'Maharashtra', '411014',
     18.5679, 73.9143, 18.5681, 73.9140, 25, '00:00', '23:59', 'OPEN'),
    ('10000000-0000-0000-0000-000000000003', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'Riverside Parking', 'Quiet riverside lot with EV charging nearby.',
     'Baner-Pashan Link Road', 'Pune', 'Maharashtra', '411021',
     18.5590, 73.7868, 18.5592, 73.7865, 15, '07:00', '22:00', 'OPEN');

INSERT INTO parking_floors (id, parking_location_id, floor_name, floor_number) VALUES
    ('10000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000001', 'P1', 1),
    ('10000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000001', 'P2', 2),
    ('10000000-0000-0000-0000-000000000021', '10000000-0000-0000-0000-000000000002', 'G', 0),
    ('10000000-0000-0000-0000-000000000031', '10000000-0000-0000-0000-000000000003', 'P1', 1);

INSERT INTO parking_zones (id, floor_id, zone_name) VALUES
    ('10000000-0000-0000-0000-000000000111', '10000000-0000-0000-0000-000000000011', 'A'),
    ('10000000-0000-0000-0000-000000000112', '10000000-0000-0000-0000-000000000011', 'B'),
    ('10000000-0000-0000-0000-000000000121', '10000000-0000-0000-0000-000000000012', 'C'),
    ('10000000-0000-0000-0000-000000000211', '10000000-0000-0000-0000-000000000021', 'A'),
    ('10000000-0000-0000-0000-000000000311', '10000000-0000-0000-0000-000000000031', 'A');

-- ------------------------------------------------------------ slots (100)
-- City Centre: P1/A 30 car + 10 2W, P1/B 10 car, P2/C 10 car = 60
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000011',
       '10000000-0000-0000-0000-000000000111', 'C' || lpad(g::text, 3, '0'), 'CAR'
FROM generate_series(1, 30) g;
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000011',
       '10000000-0000-0000-0000-000000000111', 'B' || lpad(g::text, 3, '0'), 'TWO_WHEELER'
FROM generate_series(1, 10) g;
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000011',
       '10000000-0000-0000-0000-000000000112', 'C' || lpad(g::text, 3, '0'), 'CAR'
FROM generate_series(31, 40) g;
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000012',
       '10000000-0000-0000-0000-000000000121', 'C' || lpad(g::text, 3, '0'), 'CAR'
FROM generate_series(41, 50) g;

-- Phoenix: 20 car + 5 2W = 25
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000021',
       '10000000-0000-0000-0000-000000000211', 'C' || lpad(g::text, 3, '0'), 'CAR'
FROM generate_series(1, 20) g;
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000021',
       '10000000-0000-0000-0000-000000000211', 'B' || lpad(g::text, 3, '0'), 'TWO_WHEELER'
FROM generate_series(1, 5) g;

-- Riverside: 10 car + 5 2W = 15
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000031',
       '10000000-0000-0000-0000-000000000311', 'C' || lpad(g::text, 3, '0'), 'CAR'
FROM generate_series(1, 10) g;
INSERT INTO parking_slots (parking_location_id, floor_id, zone_id, slot_number, vehicle_type)
SELECT '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000031',
       '10000000-0000-0000-0000-000000000311', 'B' || lpad(g::text, 3, '0'), 'TWO_WHEELER'
FROM generate_series(1, 5) g;

-- ----------------------------------------------------------------- pricing
-- Car ₹50/hr, two-wheeler ₹20/hr everywhere (paise).
INSERT INTO parking_pricing (parking_location_id, vehicle_type, pricing_type, hourly_rate_paise,
                             daily_rate_paise, grace_period_minutes, effective_from)
SELECT l.id, v.vehicle_type, 'HOURLY',
       CASE WHEN v.vehicle_type = 'CAR' THEN 5000 ELSE 2000 END,
       CASE WHEN v.vehicle_type = 'CAR' THEN 30000 ELSE 12000 END,
       15, now()
FROM parking_locations l CROSS JOIN (VALUES ('CAR'), ('TWO_WHEELER')) v(vehicle_type);

-- ----------------------------------------------------------------- vehicles
INSERT INTO vehicles (user_id, vehicle_number, vehicle_type, vehicle_model, vehicle_color, is_default) VALUES
    ('55555555-5555-5555-5555-555555555555', 'MH12AB1234', 'CAR', 'Swift', 'White', true),
    ('55555555-5555-5555-5555-555555555555', 'MH14EF5678', 'TWO_WHEELER', 'Activa', 'Blue', false),
    ('66666666-6666-6666-6666-666666666666', 'MH12CD9999', 'CAR', 'Creta', 'Black', true);

-- --------------------------------------------------------------- staff duty
INSERT INTO staff_shifts (staff_user_id, parking_location_id, starts_at, ends_at) VALUES
    ('33333333-3333-3333-3333-333333333333', '10000000-0000-0000-0000-000000000001',
     now() - interval '1 hour', now() + interval '7 hours'),
    ('44444444-4444-4444-4444-444444444444', '10000000-0000-0000-0000-000000000002',
     now() - interval '1 hour', now() + interval '7 hours');
