# SmartPark Pro — Entity-Relationship Diagram

Generated from `backend/src/main/resources/db/migration/V1–V6`. Money = BIGINT paise, time = timestamptz UTC.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : defines
    users ||--o{ parking_owners : "owns (1:1)"
    users ||--o{ vehicles : owns
    users ||--o{ parking_bookings : books
    users ||--o{ refresh_tokens : sessions
    users ||--o{ notifications : receives
    users ||--o{ audit_logs : acts
    users ||--o{ staff_shifts : works
    users ||--o{ support_tickets : opens
    users ||--o{ reviews : writes

    parking_owners ||--o{ parking_locations : operates
    parking_owners ||--o{ owner_ledger_entries : credited
    parking_owners ||--o{ owner_settlements : paid
    parking_owners ||--o{ owner_subscriptions : subscribed

    parking_locations ||--o{ parking_floors : has
    parking_locations ||--o{ parking_slots : contains
    parking_locations ||--o{ parking_images : shows
    parking_locations ||--o{ parking_pricing : prices
    parking_locations ||--o{ parking_bookings : hosts
    parking_floors ||--o{ parking_zones : divides
    parking_zones ||--o{ parking_slots : groups
    parking_slots ||--o{ slot_maintenance : closes
    parking_slots ||--o{ parking_booking_holds : blocks
    parking_slots ||--o{ parking_bookings : reserves
    parking_slots ||--o{ parking_sessions : occupies

    vehicles ||--o{ parking_bookings : parks
    parking_booking_holds ||--o| parking_bookings : converts
    parking_bookings ||--o{ payments : settles
    parking_bookings ||--o{ refunds : returns
    parking_bookings ||--|| qr_passes : identifies
    parking_bookings ||--o| parking_sessions : fulfills
    parking_sessions ||--o{ entry_exit_logs : records
    parking_bookings ||--o| reviews : rates

    payments ||--o{ refunds : reverses
    subscription_plans ||--o{ owner_subscriptions : offers
```

## Key constraints

| Constraint | Purpose |
|---|---|
| `no_overlapping_holds` (gist exclusion, ACTIVE only) | Holds block inventory |
| `no_overlapping_bookings` (gist exclusion, CONFIRMED/CHECKED_IN) | Double-booking impossible |
| `uq_payments_booking_captured` | **Removed in V6** — top-ups need multiple CAPTURED rows |
| `payment_webhook_events.gateway_event_id UNIQUE` | Webhook replay safety |
| `qr_passes.token_hash UNIQUE` | Opaque token lookup |
| `parking_bookings.booking_reference UNIQUE` | Customer-facing id |
| `vehicles(user_id, vehicle_number) UNIQUE` | No duplicate plates per user |
| `chk_hold_expiry` | Holds always expire after creation |
