# SmartPark Pro — API Reference (v1)

Base URL: `http://localhost:8080`. Envelope: `{ok, code, message, data, fieldErrors, timestamp, requestId}`.
Interactive docs: `/swagger-ui.html`. Auth: `Authorization: Bearer <access-token>` (roles snapshotted at login).

## Auth — `/api/v1/auth` (public, rate-limited)

| Method & path | Body | Notes |
|---|---|---|
| POST `/register` | `{fullName, email, phone?, password}` | CUSTOMER only |
| POST `/login` | `{email, password}` | → `{tokens, user}` |
| POST `/refresh` | `{refreshToken}` | Single-use rotation |
| POST `/logout` | `{refreshToken}` | Idempotent revoke |
| GET `/me` | — | Current profile + roles |

## Public parking

| Method & path | Notes |
|---|---|
| GET `/parking-locations?city&vehicleType&start&end` | Cards with rates + free counts |
| GET `/parking-locations/{id}` | Detail, pricing, rating |
| GET `/parking-locations/{id}/availability?start&end&vehicleType` | Floor→zone→slot grid |

## Customer (authenticated)

| Method & path | Notes |
|---|---|
| POST `/bookings/quote` | Backend-priced preview |
| POST `/bookings/hold` | Locks slot, 10-min hold |
| GET `/bookings/my` | Own bookings, newest first |
| GET `/bookings/{ref}` | Own booking |
| POST `/bookings/{ref}/cancel` | Pre-start only, policy refund |
| POST `/bookings/{ref}/extend` | `{newEnd}`, conflict-checked |
| GET `/bookings/{ref}/qr` | Token + PNG data URI + summary |
| GET `/bookings/{ref}/qr/png` | Printable PNG |
| POST `/payments/orders` | `{bookingReference}` → Razorpay order |
| POST `/payments/verify` | `{razorpayOrderId, razorpayPaymentId, razorpaySignature}` |
| GET `/payments/{id}` | Own (owner/admin scoped too) |
| POST `/payments/extra` | Overstay top-up order |
| GET/POST/PUT/DELETE `/vehicles`, `/vehicles/{id}` | Normalized plates |
| GET `/notifications`, POST `/{id}/read`, POST `/read-all` | In-app inbox |
| GET `/customer/dashboard` | Active/upcoming/history/vehicles/unread |
| POST `/reviews` | Completed stays, one per booking |
| POST `/support` | Open a ticket |

## Owner (`PARKING_OWNER`)

`POST /owner/onboard` (any user) · `GET /owner/me` · `GET /owner/dashboard` · locations CRUD · floors/zones · slots CRUD + `/bulk` + `/{id}/status` · pricing · `GET /owner/bookings` (+`/{ref}`, `/export` CSV) · `GET /owner/earnings` · `PUT /owner/me` · `GET /owner/reviews` · staff grant/list · shifts CRUD.

## Staff (`PARKING_STAFF`, shift-scoped)

`POST /staff/verify` · `POST /staff/check-in` · `POST /staff/check-out` · `GET /staff/active-sessions`. Identify by `qrToken`, `bookingReference`, or `vehicleNumber`.

## Admin (`SUPER_ADMIN`)

Owners list/status, subscription assign, locations list/status, bookings, users search/status, payments, refunds + `/refunds/process`, dashboard, revenue, settlements CRUD-ish, reports CSV, reviews, audit-logs, fee settings, support tickets, subscription plans.

## Webhooks (gateway-signed, public)

`POST /api/v1/webhooks/razorpay` — HMAC check, unique event id, reconcile.

## Error codes (selection)

`VALIDATION_FAILED` 400 · `UNAUTHORIZED` 401 · `INVALID_CREDENTIALS` 401 · `INVALID_TOKEN` 401 · `FORBIDDEN` 403 · `NOT_FOUND` 404 · `CONFLICT` 409 · `SLOT_UNAVAILABLE` 409 · `BOOKING_CONFLICT` 409 · `HOLD_EXPIRED` 400 · `PAYMENT_FAILED` 400/402 · `INVALID_QR` 404/409 · `ALREADY_CHECKED_IN/OUT` 409 · `BOOKING_CANCELLED` 409 · `REFUND_UNAVAILABLE` 400 · `RATE_LIMITED` 429 · `GATEWAY_NOT_CONFIGURED` 503.
