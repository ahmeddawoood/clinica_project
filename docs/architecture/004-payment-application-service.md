# Decision 004: patient payment use cases own their transactions

PaymentApplicationService owns checkout/intent creation, coupon settlement,
return-page verification and appointment ownership checks. Each public use case
opens its transaction before acquiring the existing appointment write lock.
Patient identity is passed from the authenticated Principal, never from a request
parameter. Provider metadata must still match the appointment being settled.

PaymentController binds HTTP input, maps typed outcomes to HTTP status/redirects,
sets flash messages and builds page models. The service has no dependency on
Servlet requests, MVC models, redirect attributes or ResponseEntity. HTTP 409 for
foreign payment metadata is mapped at the controller boundary.

This extraction preserves closed-appointment rules and the existing API payloads.
The real Spring/JPA coupon regression checks foreign ownership, rollback after a
notification failure, successful retry, one invoice and rejection of a duplicate
coupon. The MySQL runtime suite retains simultaneous coupon/payment/cancel checks.

Remaining work: provider amount/currency reconciliation, a shared settlement
policy with the webhook service, durable outbox delivery and shorter locks around
external provider calls. Notifications still run inside the transaction. Moving
code to a service does not solve external-delivery atomicity or make live payments
production-ready.
