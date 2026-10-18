-- V6: allow multiple CAPTURED payments per booking (extension/overstay top-ups).
-- Initial-settlement idempotency is enforced in PaymentService.verify
-- (early return on CAPTURED) plus the webhook event-id guard.
DROP INDEX IF EXISTS uq_payments_booking_captured;
