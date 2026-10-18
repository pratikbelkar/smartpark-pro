# SmartPark Pro — Production Readiness Checklist

## Secrets & access
- [ ] `JWT_SECRET` is a fresh 256-bit Base64 value (not the dev default).
- [ ] `ADMIN_PASSWORD` set; seeded super-admin password rotated after first login.
- [ ] Razorpay **live** keys + webhook secret in the secret store; test keys nowhere near prod.
- [ ] `QR_SECRET` rotated from the dev default (old QR tokens invalidate — acceptable).
- [ ] `POSTGRES_PASSWORD` strong; DB not exposed to the internet.
- [ ] `.env` (real values) git-ignored; only `.env.example` committed.

## Payments
- [ ] Live webhook URL registered; replay a test event and confirm `IGNORED` on redelivery.
- [ ] Refund scheduler observed processing a test refund end-to-end.
- [ ] Settlement process agreed with finance (manual transfers + `SETTLED` marking).

## Data & reliability
- [ ] `scripts/backup-db.sh` on a schedule; restore tested on scratch DB.
- [ ] Flyway history clean (`flyway info` shows no pending/failed).
- [ ] Health checks wired (`/actuator/health`); alerts on 5xx and payment failures.

## Security posture (known v1 limits — address before sensitive scale)
- [ ] Rate limiting is in-memory per instance (auth endpoints). Add Redis/Bucket4j when running >1 backend replica.
- [ ] Refresh tokens are bearer tokens in `localStorage`. XSS discipline required (no inline scripts, strict CSP header at the edge).
- [ ] Owner revenue aggregations scan bounded windows in Java — revisit with native rollups past ~100k bookings.
- [ ] CORS origins locked to the production domain; actuator exposure minimal.

## Operations
- [ ] Mail sender is real SMTP (Mailpit is dev-only); welcome/receipt mail observed.
- [ ] Log shipping + dashboards for bookings, captures, webhook outcomes, sweep counts.
- [ ] Frontend `VITE_API_BASE_URL` points at the public API origin.

## Compliance notes
- [ ] Razorpay test cards never used against live keys.
- [ ] No card data touches our systems (Checkout.js tokenizes; we store only gateway ids).
- [ ] Cancellation/refund policy text published to customers (matches seeded tiers).
