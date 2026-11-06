package com.example.clinic.service;
import com.example.clinic.domain.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class AppointmentLifecycleTest {
    @ParameterizedTest @ValueSource(strings={"CANCELLED","COMPLETED"})
    void paymentDoesNotReopenClosedVisit(String status) {
        Appointment a=new Appointment();a.setStatus(status);
        AppointmentLifecycle.recordSuccessfulPayment(a);
        assertThat(a.getStatus()).isEqualTo(status);
        assertThat(a.isBookingFeePaid()).isTrue();
        assertThat(a.getStripePaymentStatus()).isEqualTo("PAID_REVIEW_REQUIRED");
    }
    @ParameterizedTest @ValueSource(strings={"PENDING_PAYMENT","PENDING","CONFIRMED"})
    void paymentDoesNotRegressConfirmedVisit(String status) {
        Appointment a=new Appointment();a.setStatus(status);
        AppointmentLifecycle.recordSuccessfulPayment(a);
        assertThat(a.getStatus()).isEqualTo(status.equals("PENDING_PAYMENT")?"PENDING":status);
        assertThat(a.getStripePaymentStatus()).isEqualTo("PAID");
    }
}
