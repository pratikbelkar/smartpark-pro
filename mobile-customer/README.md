# SmartPark Pro — Customer Mobile App (Flutter)

Native customer app sharing the same backend API as the web frontend:
auth, parking search, live slot grid, quote → hold → Razorpay payment,
QR ticket, bookings, vehicles, notifications, profile. See `../mobile/README.md`
for the three-app overview.

## Run

```bash
cd smartpark-pro/mobile-customer
flutter pub get

# iOS simulator (localhost works):
flutter run

# Android emulator (localhost is the emulator itself):
flutter run --dart-define API_BASE_URL=http://10.0.2.2:8080

# Physical device on the same Wi-Fi:
flutter run --dart-define API_BASE_URL=http://<your-mac-lan-ip>:8080
```

Or press F5 in VS Code with the matching launch config (no flags to remember).

Backend must be running (`smartpark-pro/backend`, :8080) with seed data.
Demo customer: `rahul@dev.local / Password123!`. The login screen shows the
active server address; the search error card shows the request target.

## Payments

Razorpay Checkout opens inside the app (`razorpay_flutter`). Test mode:
use the test cards from `docs/RAZORPAY.md`. Never trust the Checkout
callback — the app always calls `POST /api/v1/payments/verify`, and the
booking confirms only after server verification (same rule as web).

## Notes

- Tokens live in `flutter_secure_storage` (Keychain/Keystore), never in
  plain prefs. Access token is short-lived; sign in again when it lapses.
- `android:usesCleartextTraffic` is enabled for local `http://` development.
  Production builds must use `https://` and remove that flag.
- QR tickets render offline from the stored opaque token; validity is always
  enforced server-side at the gate.
