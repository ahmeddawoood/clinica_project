# Decision 003: verified webhooks enter an application service

StripeController handles the HTTP request, configuration checks and signature
verification. Only a verified Event reaches StripeWebhookService. The service
owns the transaction, appointment locks, payment/refund transitions, invoice
writes and associated audit/activity records. Invalid signatures do not open
this write transaction.

This extraction preserves route contracts and existing settlement behavior,
including duplicate success handling and closed-appointment review status.
An integration regression forces notification failure after settlement writes,
checks rollback, then retries the signed event and checks one paid invoice.

Scope is the webhook path. PaymentController still owns return-page and payment
initiation logic. Provider amount/currency reconciliation, durable outbox delivery,
refund orchestration and operator review remain separate work. Notifications still
run inside the settlement transaction; rollback cannot retract an external message
that was already delivered. This change does not provide exactly-once delivery.
