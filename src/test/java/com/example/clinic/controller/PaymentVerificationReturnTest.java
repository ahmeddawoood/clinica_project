package com.example.clinic.controller;

import com.example.clinic.domain.*;
import com.example.clinic.repository.*;
import com.example.clinic.service.*;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentVerificationReturnTest {
    @ParameterizedTest
    @CsvSource({"intent,200,usd,true", "session,200,usd,true", "intent,199,usd,false",
            "session,199,usd,false", "intent,201,usd,false", "session,201,usd,false",
            "intent,200,eur,false", "session,200,eur,false"})
    void verifiesAmountAndCurrencyBeforeSettlement(String kind, long amount, String currency, boolean accepted) throws Exception {
        var appointments = mock(AppointmentRepository.class);
        var invoices = mock(InvoiceRepository.class);
        var stripe = mock(StripeService.class);
        var notifications = mock(NotificationService.class);
        var audit = mock(AuditLogService.class);
        var activity = mock(ActivityLogService.class);
        var payments = new PaymentApplicationService(appointments, invoices, stripe,
                new StripePaymentVerification(200L, "usd"), notifications, audit, activity);
        ReflectionTestUtils.setField(payments, "bookingFeeCents", 200L);
        var controller = new PaymentController(payments, mock(PatientService.class));
        var user = new User(); user.setEmail("owner@example.test");
        var patient = new Patient(); patient.setUser(user);
        var appointment = new Appointment(); appointment.setPatient(patient); appointment.setStatus("PENDING_PAYMENT");
        ReflectionTestUtils.setField(appointment, "id", 1L);
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));
        String intentId = null;
        String sessionId = null;
        if (kind.equals("intent")) {
            intentId = "pi_fixture";
            var intent = new PaymentIntent(); intent.setId(intentId); intent.setStatus("succeeded");
            intent.setMetadata(Map.of("appointmentId", "1")); intent.setAmount(amount);
            intent.setAmountReceived(amount); intent.setCurrency(currency);
            when(stripe.retrievePaymentIntent(intentId)).thenReturn(intent);
        } else {
            sessionId = "cs_fixture";
            var session = new Session(); session.setId(sessionId); session.setPaymentIntent("pi_fixture");
            session.setStatus("complete"); session.setPaymentStatus("paid");
            session.setMetadata(Map.of("appointmentId", "1")); session.setAmountTotal(amount); session.setCurrency(currency);
            when(stripe.retrieveSession(sessionId)).thenReturn(session);
        }
        final String pi = intentId;
        final String cs = sessionId;
        if (accepted) {
            assertThat(controller.paymentSuccess(1L, cs, pi, user::getEmail, new ExtendedModelMap()))
                    .isEqualTo("patient/payment-success");
            assertThat(appointment.isBookingFeePaid()).isTrue();
            verify(invoices).save(argThat(invoice -> invoice.getAmount().compareTo(new java.math.BigDecimal("2.00")) == 0));
        } else {
            assertThatThrownBy(() -> controller.paymentSuccess(1L, cs, pi, user::getEmail, new ExtendedModelMap()))
                    .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
            assertThat(appointment.isBookingFeePaid()).isFalse();
            verify(appointments, never()).save(any());
            verifyNoInteractions(invoices, notifications, audit, activity);
        }
    }
}
