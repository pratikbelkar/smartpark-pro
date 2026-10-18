# SmartPark Pro — Find Parking. Book Your Slot. Scan & Park.

Parking-only v1 monorepo. Docs: `docs/ARCHITECTURE.md` (locking strategy),
`docs/API.md`, `docs/RAZORPAY.md`, `docs/ER_DIAGRAM.md`, `docs/DEPLOYMENT.md`,
`docs/PRODUCTION_CHECKLIST.md`. Progress: `DEVELOPMENT_PROGRESS.md`.

## Quick start

```bash
cp .env.example .env          # fill Razorpay TEST keys for payments
docker compose up --build -d  # postgres :5433, mailpit :8025, backend :8080, frontend :3000
```

Or without Docker:

```bash
brew services start postgresql@16 && createdb smartpark
cd backend && mvn spring-boot:run        # needs Java 21
cd frontend && npm install && npm run dev  # :5173
```

- Backend: http://localhost:8080 (Swagger: `/swagger-ui.html`)
- Admin web: http://localhost:5173 (or :3000 via compose)
- Mailpit: http://localhost:8025
- Mobile apps: `docs/MOBILE_APPS.md` (customer / gate / owner, incl. Flutter Web)

## Demo accounts (seed, dev-only passwords)

| Role | Email | Password |
|---|---|---|
| Super admin | `admin@smartpark.local` | `ChangeMe123!` |
| Owner | `owner1@dev.local` | `Password123!` |
| Staff | `staff1@dev.local` | `Password123!` |
| Customer | `rahul@dev.local` | `Password123!` |

## Layout

```
smartpark-pro/
  backend/    Spring Boot API (Flyway in src/main/resources/db/migration)
  frontend/   React + TypeScript, admin console only
  mobile-customer/  Flutter customer app (search, book, pay, QR ticket)
  mobile-gate/      Flutter gate scanner app (staff camera verify + entry/exit)
  mobile-owner/     Flutter owner app (dashboard, parking, bookings, earnings)
  (all three Flutter apps also build for web — see mobile/README.md)
  docker/     nginx.conf (frontend image serves SPA + proxies /api)
  docs/       architecture, API, ER diagram, Razorpay, deployment, checklist
  scripts/    backup-db.sh
  .github/workflows/ci.yml
```

## Environment

Copy `.env.example` → `.env`. Never commit real secrets. Razorpay runs in
**test mode** until explicitly switched; no fake payments in production code.

## Tests

```bash
cd backend && mvn test        # Testcontainers Postgres, 27 tests
cd frontend && npm test && npm run build
```
