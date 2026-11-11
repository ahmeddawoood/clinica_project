# Decision 002: payment settlement does not reopen a closed appointment

Appointment state and payment state are separate. An authenticated signed payment
notification may arrive after cancellation or completion. Record the received
payment and invoice, keep CANCELLED/COMPLETED unchanged, mark payment
PAID_REVIEW_REQUIRED, and do not send the normal booking-payment success message.
The return page explains that the clinic must review settlement/refund. This flag
is not an automatic refund or a promise of delivery to an operator.

Payment return-page verification must match the provider object's appointmentId
metadata. A successful payment for another appointment is rejected without mutation.
All principal payment, webhook, doctor and patient cancellation write paths acquire
a pessimistic lock on the appointment. The expiry job locks its candidate query.
This serializes decisions about the same appointment. Repeated success events use
the persisted paid flag to avoid another invoice/notification.

No schema migration and no change to the external route contract are required.
Remaining work includes durable refund/outbox processing, an operator review queue,
provider amount/currency reconciliation, explicit state types for all transitions,
and moving payment processing out of controllers. External provider calls still
hold transaction locks. Tests here use signed synthetic events, never real charges.
