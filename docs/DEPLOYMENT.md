# SmartPark Pro — Deployment Guide

## Local development (no Docker)

```bash
brew services start postgresql@16
createdb smartpark
cp .env.example .env            # fill Razorpay TEST keys for payments
cd backend && mvn spring-boot:run      # :8080, Flyway migrates + seeds
cd frontend && npm install && npm run dev   # :5173, proxies /api → :8080
```

Demo logins (seed, dev-only): `admin@smartpark.local / ChangeMe123!`
(super-admin via `ADMIN_EMAIL/PASSWORD`), `owner1@dev.local`, `staff1@dev.local`,
`rahul@dev.local` — all `Password123!`.

## Docker Compose (recommended)

```bash
cp .env.example .env   # REQUIRED: set JWT_SECRET + ADMIN_PASSWORD at minimum
docker compose up --build -d
```

Services: `postgres` (:5433), `mailpit` (:8025 UI), `backend` (:8080),
`frontend` (:3000, serves SPA + proxies `/api`).

> Port conflicts: if another project on the machine already serves :3000
> (seen with a sibling Vite app), start with
> `FRONTEND_PORT=3101 docker compose up -d`. Backend :8080 and Postgres :5433
> are likewise overridable via `SERVER_PORT` / `POSTGRES_PORT`.

## Migration safety

- Flyway runs on every backend start (`validate-on-migrate`). Never edit an
  applied migration — add a new `V<N>__*.sql` (see V5/V6 precedent).
- `ddl-auto=validate`: entities must match the schema; CI proves it on every run.
- Destructive changes: backfill in one migration, drop in the next release.

## Database backups

```bash
./scripts/backup-db.sh ./backups   # compressed dump, 14-day retention
```

Restore: `pg_restore -U <user> -d <db> smartpark-YYYYMMDD.dump`. Test restores
quarterly on a scratch database.

## Production checklist (summary — full list in PRODUCTION_CHECKLIST.md)

1. Secrets in the platform secret store (never `.env` in git): `JWT_SECRET`
   (256-bit Base64), `ADMIN_PASSWORD`, Razorpay **live** keys + webhook secret,
   `QR_SECRET`, `POSTGRES_PASSWORD`, `MAIL_*`.
2. Razorpay: live keys, production webhook URL, settlement expectations
   (manual transfers recorded as settlements unless Route-eligible).
3. TLS: terminate at Nginx/CDN (sample 443 block below), HSTS, redirect :80.
4. `FRONTEND_URL`, `CORS_ORIGINS` set to the real domain.
5. Logs: ship container stdout to your log platform; alert on 5xx rate and
   `PAYMENT_FAILED` spikes. Health: `/actuator/health`.
6. Rotate the seeded super-admin password after first login; create named
   admin accounts.

## Sample Nginx TLS block (production edge)

```nginx
server {
    listen 443 ssl http2;
    server_name park.example.com;
    ssl_certificate /etc/letsencrypt/live/park.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/park.example.com/privkey.pem;
    add_header Strict-Transport-Security "max-age=31536000" always;

    location /api/ { proxy_pass http://backend:8080; proxy_set_header X-Forwarded-Proto https; }
    location / { root /usr/share/nginx/html; try_files $uri $uri/ /index.html; }
}
server { listen 80; server_name park.example.com; return 301 https://$host$request_uri; }
```

## Recovery procedure

1. `docker compose down` (data persists in `pgdata`).
2. Restore DB to a scratch database, verify with `SELECT count(*) FROM parking_bookings`.
3. Swap connection, `docker compose up -d backend`, confirm `/actuator/health`
   and Flyway version, then bring up `frontend`.
