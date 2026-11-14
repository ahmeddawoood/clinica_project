# Decision 005: verify the booking fee before Stripe settlement

A shared StripePaymentVerification policy is called by the signed webhook and
both patient return paths before any settlement writes. Require appointmentId
metadata to match, an exact amount in minor units, and the configured currency.
PaymentIntent must be succeeded and amount_received must equal the fee. Checkout
must be complete and paid. Missing financial fields and under/overpayments fail
closed. No appointment status, invoice or success notification changes on failure.

Mismatch returns HTTP 409. The webhook transaction rolls back before the HTTP
adapter returns that status, allowing Stripe retries. This is not an automatic
refund or operator-review queue. A valid duplicate remains a no-op after payment.

The current product has one configured fixed booking fee. Verification uses
stripe.booking-fee-cents and stripe.currency, matching payment creation. There is
no per-payment price snapshot: changing price/currency with outstanding payments
can reject valid older payments and requires operator reconciliation. Do not
reinterpret an old payment as the new price. Snapshot pricing and invoice currency
presentation remain separate work. Coupon/demo paths do not use Stripe evidence.

References: Stripe PaymentIntent object (amount, amount_received, currency) and
Checkout Session object (amount_total, currency, payment_status):
- https://docs.stripe.com/api/payment_intents/object
- https://docs.stripe.com/api/checkout/sessions/object
