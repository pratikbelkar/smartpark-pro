# DEVELOPMENT_PROGRESS.md — SmartPark Pro (parking-only v1)

## Completed
- [2026-10-08] **Step 1 — Repository inspection.** Inspected
  `~/Projects/smart-parking-backend` (SB 3.5.13, Java 17, MySQL, ddl-auto,
  JWT, simulated payments, ZXing QR, movie+cinema domain, no tests) and
  `~/Projects/smart-parking-frontend` (React 19 JSX, axios only, no router,
  movie+parking UI). Full findings in chat + `docs/ARCHITECTURE.md` (reuse list).
- [2026-10-08] **Monorepo scaffold** in `smartpark-pro/`: README, `.env.example`,
  `.gitignore`, `docker-compose.yml` (postgres:16 + mailpit), `docs/ARCHITECTURE.md`
  (incl. locking strategy), `frontend/package.json` (target stack), backend `pom.xml`
  (SB 3.5.13 / Java 21 / Postgres / Flyway / JJWT 0.12.6 / ZXing 3.5.3 /
  razorpay-java 1.4.7 / springdoc 2.8.9 / MapStruct / Testcontainers),
  `application.yml`, Flyway `V1__identity` + `V2__parking` + `V3__booking`.
- [2026-10-08] Local `smartpark` database created (Homebrew Postgres 16).

## Outstanding (in spec order)
1. ~~Verify V1–V3 migrations~~ — done 2026-10-08 (Flyway V1→V3 clean on empty
   schema; exclusion constraint proven: overlap rejected, adjacent accepted).
   Still open: seed migration (owners, locations, 100 slots, staff, customers,
   pricing) — queued with Step 3.
2. Step 2: backend foundation — entities, DTOs, exception envelope, JWT +
   refresh rotation, `SUPER_ADMIN` seeding (not public), OpenAPI.
3. Step 3: parking management (owner onboarding, locations, floors/zones/slots,
   pricing, live grid availability queries).
4. Step 4: booking engine (quote → hold → confirm, pessimistic locking,
   lifecycle sweeps, extend, cancel) + concurrency tests.
5. Step 5: Razorpay test-mode (orders, checkout, verify, webhooks, refunds).
6. Step 6: QR passes + staff scanner + check-in/out + sessions.
7. Steps 7–9: customer / admin / owner frontends on the declared stack.
8. Steps 10–12: notifications, reports/CSV, Docker/Nginx/CI, guides.

## Key decisions
- New `smartpark-pro/` monorepo in this workspace; legacy `~/Projects/*` kept
  untouched as reference. No movie/cinema code is ported (v1 is parking-only).
- Java 21 via `brew install openjdk@21` (machine had only 17); Postgres via
  Homebrew (5432) for dev, compose maps 5433 to avoid clash.
- Money = BIGINT paise; timestamps = timestamptz UTC; UUID PKs; Flyway owns schema.
- Razorpay test keys go in `.env` (never committed); simulated gateway is dropped.

## Step 2 status (done 2026-10-08)
Identity module + security foundation, all passing:
`SmartParkApplication`, `ApiResponse` envelope, `ErrorCode` + `ApiException` +
`GlobalExceptionHandler` (validation 400 w/ fieldErrors, overlap→409
SLOT_UNAVAILABLE, JSON 401/403, no stack-trace leaks), `JwtService`
(access JWT) + opaque SHA-256 refresh rotation, `RateLimitFilter` (30/min/IP on
auth), `SecurityConfig` (role rules for /admin /owner /staff), `AuthService`
(register CUSTOMER-only, login, refresh, logout, /me) + `AuthController`,
`User/Role/UserRole/RefreshToken` + repos, after-commit `AuditService`,
`NotificationService`, env-gated `AdminBootstrap` (super admin, never public),
swagger bearer scheme, `AuthFlowIT` (Testcontainers PG16) green.

## Test status
- `AuthFlowIT.fullAuthLifecycle` PASS (register→duplicate 409→login→bad-login
  401→/me→anon 401→refresh rotation→replay 401→logout→validation 400).
- Test env notes: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` +
  `TESTCONTAINERS_RYUK_DISABLED=true` required (Colima); default JWT secret
  replaced with valid Base64 dev secret.
- Dev port: SmartPark runs on **8081** locally (legacy Park_System occupies
  8080); switch via `SERVER_PORT`.

## Step 3 status (done 2026-10-08)
Parking module, all passing:
- Entities + repos: `ParkingOwner/Location/Image/Floor/Zone/Slot`,
  `SlotMaintenance`, `ParkingPricing` (versioned, paise), `Vehicle`, plus all
  Step-4-ready entities (holds, bookings, payments, refunds, webhooks, QR,
  sessions, logs, reviews, ledger, settlements, fees, policies, plans,
  subscriptions, shifts, tickets).
- `OwnerService`: onboard (PENDING) → admin approve/suspend/reject (role
  granted on approve) + approved-owner tenant resolver used by every owner API.
- `ParkingService`: owner-scoped location/floor/zone/slot CRUD, bulk generate
  (C001-style, skips existing), slot status incl. maintenance windows, versioned
  pricing (old row closed, never rewritten), public search/detail, window-based
  availability grid (AVAILABLE/RESERVED/OCCUPIED/MAINTENANCE from holds +
  bookings + maintenance + sensor state).
- `VehicleService`: normalized CRUD, per-user uniqueness, single default,
  delete blocked by active bookings.
- Controllers: public `/parking-locations` (+`/{id}`, `/{id}/availability`),
  `/owner/**`, `/vehicles`, admin `/admin/owners`, `/admin/parking-locations`.
- `V4__seed`: 2 owners, 3 Pune locations, 100 slots, pricing (car ₹50/hr,
  2W ₹20/hr), staff+shifts, customers+vehicles (all `Password123!`, dev-only).
- `V5__entry_exit_created_at`: fixed entity/migration drift found by
  `ddl-auto=validate`.

## Test status
- `mvn test`: **3/3 green** (`AuthFlowIT` + `ParkingFlowIT` × 2, Testcontainers
  PG16). Covers: seed integrity, search + paise pricing, availability states
  incl. hold-blocked slot, cross-owner 403, anon 401, onboard→PENDING→approve→
  build flow, bulk create, JWT role snapshot (re-login needed after approval).
- Bugs fixed by tests: V5 drift, derived-query name, public availability URL
  not in permitAll, floor/zone entity leak into JSON (DTOs added), onboard
  route behind owner-role rule, surefire `*IT` includes.

## Frontend contract notes (for Step 7)
- Roles are snapshotted in the access JWT: after approval/role change the user
  must sign in again (or hit refresh).
- Money in APIs is paise except `PricingRequest.hourlyRateRupees` (decimal).
- Slot states for the grid: `AVAILABLE` emerald, `RESERVED` amber,
  `OCCUPIED` red, `MAINTENANCE` grey — always with text labels.

## Step 4 status (done 2026-10-08)
Booking engine, all passing:
- `PricingService`: sole price authority — ceil-hour billing minus grace,
  daily cap, weekend-type preference, convenience fee + commission% snapshot.
- `BookingService`: `quote` (no persistence) → `hold` (FOR UPDATE slot lock +
  maintenance/hold/booking overlap checks → ACTIVE hold + PENDING_PAYMENT
  booking with price snapshot) → `confirmBooking` (Step 5 hook, idempotent).
  Cancel (pre-start only, tiered % of parking+tax back, convenience fee never
  refunded, PENDING refund row when paid), extend (slot-locked conflict check
  excluding self, requote delta → PENDING when extra due).
- `BookingLifecycleService` (every 60s, idempotent): hold expiry → unpaid
  booking expiry → no-show past grace (never touches checked-in) → 30-min
  reminders with notification dedupe.
- Controllers: customer `/bookings` (quote/hold/my/get/cancel/extend),
  owner `/owner/bookings` (tenant-scoped), admin `/admin/bookings`.
- Max 24h per booking; PENDING_PAYMENT excluded from the DB exclusion
  predicate by design (holds block instead).

## Test status
- `mvn test`: **10/10 green**. New `BookingEngineIT` (7 tests): backend paise
  math (3h×₹50+₹10=₹160), hold blocks double-hold (409), **4-thread race on
  one slot → exactly 1 winner**, sweeps (expiry + NO_SHOW), cancel tiers
  (100% >24h incl. PENDING refund row, 0% <4h), extend repricing (+₹50/hr,
  conflict → 409), API round-trip incl. cross-customer 403 + owner/admin reads.
- Bugs fixed by tests: sweep self-invocation skipped the tx proxy (`runOnce`
  now owns the transaction); test-only backdating moved to SQL (respects
  `chk_hold_expiry`).

## Step 5 status (done 2026-10-08)
Razorpay test-mode, all passing:
- `RazorpayGateway` interface + live SDK impl (`orders.create`, `payments.fetch`,
  `capture`, `refund`, HMAC verify). Missing keys → 503, never fake success.
- `PaymentService`: order create/reuse (idempotent, amount-due aware incl.
  extension top-ups), verify (HMAC → authoritative fetch → capture if
  authorized → CAPTURED + `confirmBooking` + exact ledger split), replay-safe
  webhooks (`gateway_event_id` unique; captured/failed/refund routes),
  `processPendingRefunds` every 5 min, scoped payment reads.
- Ledger (exact): convenience fee covered first (platform), then parking;
  owner gets parking − commission%, commission recorded separately; refunds
  post REFUND_DEBIT; partial vs full distinguished.
- Controllers: `/payments/orders|verify|{id}`, public signed
  `/webhooks/razorpay`, admin `/admin/refunds` + `/refunds/process`.
- `docs/RAZORPAY.md`: test keys, tunnel webhooks, test cards, go-live list.

## Test status
- `mvn test`: **16/16 green**. New `PaymentFlowTest` (6 tests, mocked gateway
  boundary): order/verify/confirm + idempotency, webhook capture + replay
  IGNORE + forged-signature 401, expired-hold 410, cancel→refund→scheduler
  settlement (PARTIALLY_REFUNDED), customer API round-trip incl. keyId.
- Real bug caught by tests: Razorpay SDK `verify*` returns **boolean**
  (no throw on mismatch) — forged signatures would have passed. Now checked.

## Frontend contract notes (for Step 7)
- Checkout: `POST /payments/orders {bookingReference}` → `{keyId, orderId,
  amountPaise}` → Razorpay Checkout → `POST /payments/verify
  {razorpayOrderId, razorpayPaymentId, razorpaySignature}` → booking CONFIRMED.
- Success is only real after verify/webhook; never trust the Checkout callback.

## Step 6 status (done 2026-10-09)
QR + gate operations, all passing:
- `QrService`: opaque HMAC-derived tokens (recomputable for display, SHA-256
  lookup index, revocable, `end+3h` expiry), ZXing PNG + data-URI rendering.
  Issued on payment confirm, revoked on cancel.
- `CheckinService` (`/staff/verify|check-in|check-out|active-sessions`):
  QR/ref/plate resolution, payment + 30-min-early window + vehicle-match
  gating, shift-based location scope, sessions, physical occupancy flip,
  entry/exit audit logs, overstay billing (ceil-hour × current rate).
- Gate never blocks on money: overstay creates a top-up order via
  `POST /payments/extra`, settled with the normal verify flow.
- Customer: `GET /bookings/{ref}/qr` (token + PNG + summary),
  `GET /bookings/{ref}/qr/png` (printable).
- `V6__topup_payments`: dropped the captured-payments partial unique index
  (top-ups legitimately add CAPTURED rows; idempotency stays in code).

## Test status
- `mvn test`: **22/22 green**. New `QrCheckinIT` (6 tests): ticket + PNG
  magic bytes, verify→check-in→checkout incl. double-scan 409s, cross-location
  staff 403, plate mismatch 409, overstay (1h×₹50) → top-up order → settle,
  cancel revokes QR, full QR scan round-trip.
- Bugs fixed by tests: transient-User flush in entry/exit logs (staff row now
  loaded), partial-unique-index vs top-ups (V6).

## Frontend contract notes (for Step 7)
- Staff UI needs a QR scanner (browser lib) + manual ref/plate fallback calling
  `/staff/verify`, then check-in/out buttons gated by `canCheckIn/canCheckOut`
  + reason strings.
- Customer ticket page: show `dataUri` image + booking summary; print uses the
  `/qr/png` endpoint.

## Step 7 status (done 2026-10-09)
Customer frontend (`frontend/`, React 19 + TS + Vite + Tailwind v4), all on
real APIs:
- Landing (hero + search + how-it-works), Login/Register (RHF + Zod), Search
  (city/vehicle/window filters, live free-slot counts), LocationDetail
  (visual slot grid with emerald/amber/red/grey states, pick or auto-allocate).
- Booking flow: server-priced quote → hold with expiry countdown context →
  Razorpay Checkout (dynamic script load) → server verify → confirmation.
  Never trusts the Checkout callback; closed modals show "check My Bookings".
- My Bookings, BookingDetail (cancel per policy, extend, QR ticket + print
  PNG, settle-dues link), Saved Vehicles, Notifications, Profile dashboard.
- Auth: access token in sessionStorage, refresh in localStorage, axios
  interceptor with single-flight silent refresh; `RequireAuth` route guard.
- New backend endpoints required by the UI: `GET /notifications` (+read),
  `GET /customer/dashboard`. Fixed a real bug found by E2E: Notification
  entity leaked its lazy `user` into JSON (DTO-mapped now).

## Test status
- Backend `mvn test`: **22/22 green** (unchanged).
- Frontend `npm test` (vitest): **5/5** (money/date formatting, status-style
  coverage for all booking + slot states). `npm run build` clean (one
  chunk-size warning, accepted for v1).
- Live E2E (backend :8080 + Vite :5174, seeded DB): register → search →
  availability → quote (₹160) → hold → my bookings → vehicle → notifications
  → dashboard → order correctly `503 GATEWAY_NOT_CONFIGURED` without keys.

## Steps 8–9 status (done 2026-10-09)
Owner / admin / staff, all passing:
- Backend: `OwnerDashboardService` (stats, earnings by location, bookings CSV,
  profile), `OwnerStaffService` (grant role, roster/delete shifts, all
  tenant-scoped), `AdminDashboardService` (15 cards + 14d bookings, 30d
  revenue, status pie, vehicle split, top locations, 24h peaks),
  `AdminOpsService` (users search + suspend, payments, reviews, audit,
  versioned fees, settlements with balance guard + ledger debit, support
  tickets, plans + assignment, bookings CSV), customer `POST /support` and
  `POST /reviews` (completed stays, one per booking).
- Frontend: owner console (dashboard, locations, structure/slots/pricing
  manager, bookings + CSV, earnings ledger, staff/shifts, reviews, settings),
  premium admin console (recharts dashboard, owners + plans, locations,
  bookings lookup, users, payments + refund queue, revenue, settlements,
  reports, reviews, audit, fees, support), staff gate scanner (camera via
  html5-qrcode + manual ref/plate fallback, verify → approve entry/exit with
  reason strings). Role links in navbar, `RequireRole` guards.

## Test status
- `mvn test`: **26/26 green** (new `AdminConsoleIT`, 4 tests: owner stats +
  CSV export, shift tenant isolation, admin dashboard shape + suspend/reactivate
  + versioned fees + support round-trip, settlement guard + settle + plans).
- Frontend `npm test`: **5/5**, `npm run build` clean (chunk-size warning only).
- Live E2E (:8080 + :5174): owner dashboard/earnings/export, admin dashboard
  cards + charts data, users search, staff sessions — all 200.

## Steps 10–12 status (done 2026-10-09)
- Notifications: async best-effort `EmailService` (welcome, confirmation,
  cancel, refund, checkout receipt) via Spring Mail → Mailpit (:8025) in dev;
  in-app inbox already shipped. Push/SMS/WhatsApp remain optional/future.
- Reports: owner + admin bookings CSV downloads (auth-gated); PDF deferred
  until core demand exists.
- Deployment: multi-stage `backend/Dockerfile` + `frontend/Dockerfile`
  (nginx serves SPA + proxies `/api`), `docker/nginx.conf` (+ TLS sample in
  guide), full-stack `docker-compose.yml`, `scripts/backup-db.sh` (14-day
  retention), `.github/workflows/ci.yml` (backend Testcontainers suite +
  frontend test/build).
- Docs: `API.md` (endpoint catalog), `ER_DIAGRAM.md` (mermaid + constraints),
  `RAZORPAY.md`, `DEPLOYMENT.md` (compose, migrations, backups, recovery),
  `PRODUCTION_CHECKLIST.md` (incl. honest v1 limits: in-memory rate limits,
  localStorage refresh tokens, Java-side aggregations).

## Test status
- `mvn test`: **27/27 green** (new `EmailFlowTest`: welcome mail asserted via
  mocked sender with async timeout verification).
- Frontend `npm test`: **5/5**, `npm run build` clean.
- Docker images build (verified `docker compose build`).

## Maps, imagery & design pass (done 2026-10-09)
- Web: Leaflet/OSM maps (no API key needed) — Search has List/Map tabs with
  live 30s refresh, free-slot badges, geolocation "Near me" + haversine
  distances and nearest-first sorting; LocationDetail has a photo hero, live
  mini-map and Google-Maps directions link (entry gate coords). Owner create
  form has click-to-place map pickers for the pin + entry gate (persisted via
  existing lat/lng fields — no backend change needed).
- Verified real parking imagery (6 hotlinked Unsplash JPEGs, HTTP-checked)
  with graceful gradient fallback (`SafeImage`), used on landing, cards, and
  detail heroes; owner uploads override automatically.
- Mobile: `flutter_map` search map + tappable markers, hero images + distance
  badges on cards, detail hero + getting-there mini-map, geolocator + Android/
  iOS permission entries. `flutter analyze` clean, debug APK builds.

## Separate mobile apps (done 2026-10-10)
Split into three standalone apps sharing one backend:
- `mobile-customer` (`com.smartpark.smartpark_customer`): search, live slot
  grid, quote → hold → Razorpay → QR ticket, bookings, vehicles, notifications.
- `mobile-gate` (`com.smartpark.smartpark_gate`): camera QR scanning
  (`mobile_scanner`, torch/flip/frame), manual ref/plate fallback, verify →
  approve entry / record exit, parked-now list, staff-only guard screen.
- `mobile-owner` (`com.smartpark.smartpark_owner`): dashboard stats, locations
  CRUD, per-location structure/slots/pricing manager, bookings, earnings
  ledger, staff grants + shift roster view, reviews, profile; non-owners get
  an in-app onboarding application. All APIs live and tenant-scoped.
- Each app: own VS Code launch configs, secure token storage, platform-aware
  API base URL. `flutter analyze` clean on all three; all three debug APKs build.

## Admin-only React + Flutter Web (done 2026-10-10)
- React pruned to the admin console (+ login): customer/owner/staff pages,
  maps, Leaflet deps and register page removed; root redirects by role;
  build + tests green. Customers/owners/staff are mobile-app (+ Flutter Web) only.
- `flutter build web` verified for all three apps (served + loading).
  Customer payments use Checkout.js on web via `dart:js_interop`
  (`pay_mobile.dart` / `pay_web.dart` conditional dispatch — same verify flow).
  Notable: the Razorpay SDK's `verify*` returns boolean (already guarded);
  the `as pay` prefixed conditional import mis-resolved, canonical re-export
  used instead. Camera/geolocation/storage all have web plugin support.

## Mobile app status (done 2026-10-09)
Flutter 3.44 customer app (`mobile-customer`, `com.smartpark.smartpark_customer`):
search with city/vehicle/time pickers, live slot grid, quote → hold →
Razorpay Checkout → server verify, QR ticket (`qr_flutter`), bookings,
vehicles, notifications, profile. Tokens in secure storage, configurable
`API_BASE_URL` per platform, `flutter analyze` clean, debug APK builds.
See `mobile/README.md` for run instructions (incl. 10.0.2.2 for the Android
emulator and the cleartext-HTTP dev note).

## Gate app + console redesign (done 2026-10-09)
- Mobile gate app: staff-role home (`Gate` tab) with live camera scanning
  (`mobile_scanner`, torch + camera flip, scan frame, pause/resume), manual
  ref/plate fallback, verify → approve entry / record exit with reason
  strings; camera + location permissions on Android/iOS. Analyzer clean, APK
  builds. Staff sign in with the same credentials (`staff1@dev.local`).
- Web consoles redesigned: dark sidebar navigation (animated active pill),
  mobile drawer, animated stat counters, gradient-accent cards, page
  transitions, scroll-in landing animations (framer-motion). Owner, admin and
  gate routes all wrapped; fully responsive.

## Known limitations (v1)
Single-instance rate limits; bearer refresh tokens (XSS discipline + CSP at
edge); owner analytics computed in Java over bounded windows; no PDF reports;
no push/SMS; settlement payouts are manual transfers recorded in-app.

## Final verification (2026-10-09)
- `mvn test`: **28/28 green** (incl. `ParkingFlowIT.publicSearchWithoutCity`
  regression for a real `lower(bytea)` crash found via Docker).
- Frontend `npm test`: **5/5**, `npm run build` clean.
- `docker compose build` green (fixed: bad `FROM` tag, missing `@types/node`,
  nginx.conf context); full stack booted and verified through nginx
  (`Server: nginx/1.31.6`): SPA 200, `/api` search 200, register 200.
- Local `.env` created from the example (git-ignored); machine also runs a
  sibling app on :3000/:8080 at times — use `FRONTEND_PORT=3101` if needed.
