# SmartPark Pro — Architecture

Parking-only v1. `smartpark-pro/` monorepo: `backend/` (Spring Boot 3.5, Java 21,
PostgreSQL 16 + Flyway), `frontend/` (React + TS + Vite, planned Step 7),
`docker/` + `docker-compose.yml`, `docs/`, `scripts/`.

## Request flow (customer booking)

```
SEARCH → LOCATION → VEHICLE → DATE/TIME → SLOT (pick or auto)
  → QUOTE (backend-priced) → HOLD (10 min, slot row locked)
  → RAZORPAY ORDER → CHECKOUT → VERIFY SIGNATURE → CONFIRM (transactional)
  → QR PASS → ARRIVE → SCAN → CHECK-IN → PARK → EXTEND? → CHECK-OUT → RECEIPT
```

## Money

- Stored as `BIGINT` **paise** everywhere (`*_paise`). Never floats.
- Backend is the sole price authority: quote → hold → order all recompute
  server-side from `parking_pricing` + `platform_fees` effective rows.
- Confirmed bookings keep a `pricing_snapshot` JSONB; later fee/price changes
  never rewrite history.
- Commission/convenience fee are ledger entries, not inline math at payout time.

## Locking strategy (concurrency safety)

Double-booking prevention is **three layers**, in order:

1. **Pessimistic row lock (primary).** Every hold/confirm/extend runs in a
   single `@Transactional` method that first executes
   `SELECT ... FOR UPDATE` on the `parking_slots` row. Concurrent requests for
   the same slot serialize on this row lock; the loser re-checks availability
   after the winner commits and fails cleanly with `SLOT_UNAVAILABLE`.
   WebSocket/eventual state is never used for inventory decisions.

2. **Application overlap check.** Inside the same transaction, query for
   `ACTIVE` holds and `CONFIRMED`/`CHECKED_IN` bookings whose
   `[start_time, end_time)` overlaps the requested interval
   (`start < :end AND end > :start`). Holds are short-lived (10 min) and also
   block inventory, so both tables are checked.

3. **Exclusion-constraint backstop.** `no_overlapping_holds` and
   `no_overlapping_bookings` (`EXCLUDE USING gist (slot WITH =, range WITH &&)`
   with partial predicates) make an overlapping commit *impossible* even if
   two transactions race past the app check. A violation surfaces as a
   `DataIntegrityViolationException`, mapped to HTTP 409 `SLOT_UNAVAILABLE`.
   Requires `btree_gist` (installed in V3).

Related rules:
- `physical_occupancy_status` (FREE/OCCUPIED) is sensor/observed state only and
  is never consulted for booking availability.
- Checked-in sessions are never expired by the no-show sweeper.
- Extensions re-run the full overlap check excluding the booking itself.
- Payments: exactly-once capture via the verify early-return on CAPTURED plus
  `gateway_event_id UNIQUE` webhook replay safety. (A partial unique index on
  captured payments was removed in V6: extension/overstay top-ups legitimately
  create additional CAPTURED rows per booking.)

## QR passes

- QR encodes an **opaque random token only** (no PII). Only its SHA-256 hash is
  stored (`qr_passes.token_hash`). Every scan is verified server-side against
  booking + payment + time window + vehicle.
- PNG rendering uses ZXing; scanning uses a browser QR library in the staff UI.

## Roles & tenant isolation

- `SUPER_ADMIN` (seeded, never public registration), `PARKING_OWNER`,
  `PARKING_STAFF`, `CUSTOMER`.
- Owners are scoped by `owner_id` on every query (tenant isolation enforced in
  service layer + ownership checks, not just URL design).
- Staff are scoped to assigned locations via `staff_shifts`.

## Reuse from the legacy system (`~/Projects/smart-parking-backend`)

Kept concepts (rewritten into `com.smartpark`, parking-only):
stateless JWT filter + `AuthUser`, `ApiException` + global handler envelope,
`CodeGenerator`, slot allocation preference (nearest-to-entrance), scheduler
sweeps (hold expiry, grace/no-show, reminders), ZXing QR pipeline.
Deliberately dropped: all movie/cinema domain, simulated gateway (replaced by
real Razorpay test-mode flow), `ddl-auto=update` (Flyway is source of truth),
MySQL-isms, Lombok `@Data` on entities (use `@Getter/@Setter` + excluded
equals/hashCode to avoid proxy recursion).
