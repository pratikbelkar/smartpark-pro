# SmartPark Pro — Mobile Apps (separate)

Three standalone apps sharing one backend API:

| App | Folder | Package | Who signs in |
|---|---|---|---|
| Customer | `mobile-customer` | `com.smartpark.smartpark_customer` | `rahul@dev.local / Password123!` |
| Gate (scanner) | `mobile-gate` | `com.smartpark.smartpark_gate` | `staff1@dev.local / Password123!` |
| Owner | `mobile-owner` | `com.smartpark.smartpark_owner` | `owner1@dev.local / Password123!` |

## Run any of them (mobile)

```bash
cd smartpark-pro/mobile-gate   # or mobile-owner / mobile-customer
flutter pub get
flutter run --dart-define API_BASE_URL=http://10.0.2.2:8080  # Android emulator
```

Or open the folder in VS Code and press F5 with the matching launch config —
no flags to remember. The app defaults to `10.0.2.2` on Android and
`localhost` elsewhere; `--dart-define` always wins (physical device → Mac LAN IP).

Backend must be running (`smartpark-pro/backend`, :8080) with seed data.

### If an app can't reach the server

Each app shows its target server on the login screen and in every error card.
If requests fail, open **Profile → Server** (customer), **More → Server**
(owner) or the profile tab (gate) and set the address explicitly:

- Android emulator: `http://10.0.2.2:8080`
- iOS simulator / desktop: `http://localhost:8080`
- Physical phone (same Wi-Fi as the Mac): `http://<mac-lan-ip>:8080`
- Empty the field to return to automatic.

No rebuild needed — the change applies immediately. The usual causes are a
stale install (reinstall with F5), a sleeping emulator route (cold-boot it),
or the backend simply not running.

## What each app does

- **Customer**: search, live slot grid, quote → hold → Razorpay → QR ticket,
  bookings, vehicles, notifications. Login screen shows the active server.
- **Gate**: camera QR scanning (torch, camera flip, scan frame), manual
  ref/plate fallback, verify → approve entry / record exit, parked-now list.
  Refuses non-staff accounts with an explanatory screen.
- **Owner**: dashboard stats, locations CRUD, per-location structure / slots /
  pricing manager, bookings, earnings ledger, staff grants + shift roster view,
  reviews, profile; non-owners get an in-app onboarding application screen.

## Flutter Web (run the apps in a browser)

All three compile with `flutter build web` (verified). Serve the output, e.g.:

```bash
cd smartpark-pro/mobile-customer/build/web && python3 -m http.server 4173
```

Open http://localhost:4173 (already in the backend CORS allow-list).
Web differences: payments use Razorpay **Checkout.js** (plugin is mobile-only,
same verify flow); secure storage falls back to browser storage; gate camera
needs `localhost`/HTTPS; geolocation uses the browser prompt.

## Shared rules (all apps)

- Tokens in `flutter_secure_storage`, never plain prefs.
- Payments confirm only after `POST /api/v1/payments/verify` succeeds.
- `android:usesCleartextTraffic` is dev-only; production builds use `https://`.
