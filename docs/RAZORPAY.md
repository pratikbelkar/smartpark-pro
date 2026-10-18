# Razorpay — test mode setup and operations

## 1. Get test keys

1. Sign up at [razorpay.com](https://razorpay.com) and stay in **Test Mode**
   (dashboard toggle, top-right).
2. Go to **Settings → API Keys → Generate Test Keys**.
3. Copy into `.env` (never commit real keys):

```bash
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
RAZORPAY_WEBHOOK_SECRET=whsec_from_step_3
```

4. Restart the backend. Without keys, `/payments/orders` returns
   `503 GATEWAY_NOT_CONFIGURED` — by design, never a fake success.

## 2. Webhooks (local development)

Razorpay cannot reach `localhost`, so during development either:

- Use a tunnel (`ngrok http 8081`) and register
  `https://<tunnel>/api/v1/webhooks/razorpay` under
  **Settings → Webhooks** with events `payment.captured`, `payment.failed`,
  `refund.processed`; copy the webhook secret into `.env`, or
- Rely on `/payments/verify` (synchronous) — webhooks then act only as a
  reconciliation backstop.

Replay safety: `gateway_event_id` is unique; re-deliveries return `IGNORED`.

## 3. Test cards (Razorpay test mode)

- Card: `4111 1111 1111 1111`, any future expiry, any CVV.
- UPI: use the test UPI handle shown on the Checkout page.
- Netbanking: select any test bank.

## 4. Going live checklist

- [ ] Switch dashboard to **Live Mode**, generate **live** keys into the
      production secret store (never `.env` in git).
- [ ] Register the production webhook URL + secret.
- [ ] Confirm settlement expectations: standard accounts settle to the nodal
      account; owner payouts are manual transfers recorded as
      `owner_settlements` (Razorpay Route only if the account is eligible).
- [ ] Run `POST /api/v1/admin/refunds/process` dry-run and the full
      `PaymentFlowTest` suite against test keys first.
