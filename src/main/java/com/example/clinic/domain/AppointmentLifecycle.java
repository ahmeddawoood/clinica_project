package com.example.clinic.domain;

/** Appointment state and payment state are separate: a late payment never reopens a visit. */
public final class AppointmentLifecycle {
    private AppointmentLifecycle() {}

    public static boolean isClosed(Appointment appointment) {
        return "CANCELLED".equals(appointment.getStatus()) || "COMPLETED".equals(appointment.getStatus());
    }

    public static void recordSuccessfulPayment(Appointment appointment) {
        appointment.setBookingFeePaid(true);
        if (isClosed(appointment)) {
            appointment.setStripePaymentStatus("PAID_REVIEW_REQUIRED");
        } else {
            if ("PENDING_PAYMENT".equals(appointment.getStatus())) appointment.setStatus("PENDING");
            appointment.setStripePaymentStatus("PAID");
        }
    }
}
