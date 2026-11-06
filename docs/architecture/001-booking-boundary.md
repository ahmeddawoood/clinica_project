# Decision 001: one booking policy, one transactional write boundary

## Problem
The booking form accepted arbitrary timestamps, the write path enforced fixed
08:00–18:00 hours, while the slot endpoint used the doctor's weekly schedule.
Patients could submit an hour that the picker would not advertise. Exact-time
conflict checks also treated 10:00 and legacy 10:30 bookings as different slots.

## Decision
Keep the Spring MVC/Thymeleaf application and one MySQL database. Introduce
AppointmentBookingService for booking/cancellation and AppointmentAvailabilityService
for shared working-hour rules. PatientService retains patient/profile queries and
acts as a compatibility facade for existing callers. This is a first extraction,
not a completed modular-monolith migration.

A slot is one wall-clock hour in the application's current clinic timezone.
New bookings must start on the hour, lie in a configured working interval, and
be in the future. Missing weekly configuration retains the existing 08–18 default.
Any non-cancelled legacy appointment in that hour blocks the slot. Queries for the
picker are limited to the selected day. No database migration changes historical
appointment timestamps.

The write transaction uses READ_COMMITTED and locks the doctor row before checking
conflicts and saving. A real two-patient race reproduced duplicate bookings with
MySQL REPEATABLE_READ: patient lookup established a snapshot before the competing
booking committed. READ_COMMITTED lets the conflict query observe the committed
winner after waiting for the doctor lock.
The picker is advisory; a concurrent winner can invalidate a displayed slot. The
losing request receives a recoverable error and must select again. The booking UI
clears old selections on date/doctor changes and ignores outdated network replies.

The UI uses progressive sections and a live summary. Its primary action says
"Continue to payment" because saving creates PENDING_PAYMENT, not a confirmed
appointment. AI specialty suggestions are removed from this form to keep booking
focused; existing AI endpoints and other assistant functionality remain unchanged.

## Trade-offs and remaining work
Doctor-level locking serializes different slots for the same doctor. External AI
analysis and legacy notification calls still occur inside the transaction. Moving
these to a durable outbox and shortening lock duration is a separate change.
Cancellation authorization still relies on callers. Payment/cancellation races,
formal state transitions, explicit multi-timezone support, schedule edits racing
with bookings, and bounded pagination outside slot lookup remain follow-up work.
Do not claim this iteration completes those guarantees.

## Evidence
Unit tests cover custom hours, unavailable days, invalid timestamps and held slots.
The MySQL browser workflow sends simultaneous patient requests, checks exactly one
successful booking, tests slot-loading failure/retry, submits the real form and
captures desktop/mobile screenshots. Existing payment, doctor confirmation,
notification, role-access and recovery tests remain enabled.
