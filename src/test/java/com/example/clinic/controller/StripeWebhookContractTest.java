package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Patient;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.service.*;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StripeWebhookContractTest {
    private static final String SECRET="whsec_local_fixture_only";
    private final AppointmentRepository appointments=mock(AppointmentRepository.class);
    private final InvoiceRepository invoices=mock(InvoiceRepository.class);
    private final NotificationService notifications=mock(NotificationService.class);
    private final AuditLogService audit=mock(AuditLogService.class);
    private final ActivityLogService activity=mock(ActivityLogService.class);
    private final StripeWebhookService settlement=new StripeWebhookService(appointments,invoices,notifications,audit,activity);
    private final StripeController controller=new StripeController(settlement);

    private String payload(String type) {
        return "{\"id\":\"evt_fixture\",\"object\":\"event\",\"api_version\":\""+Stripe.API_VERSION
                +"\",\"type\":\""+type+"\",\"data\":{\"object\":{\"id\":\"pi_fixture\",\"object\":\"payment_intent\","
                +"\"amount\":200,\"currency\":\"usd\",\"status\":\"succeeded\",\"metadata\":{\"appointmentId\":\"1\"}}}}";
    }
    private String signature(String body,long timestamp) throws Exception {
        var mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
    private void configured() { ReflectionTestUtils.setField(controller,"webhookSecret",SECRET); }
    private void noEffects() { verifyNoInteractions(appointments,invoices,notifications,audit,activity); }

    @Test
    void missingConfigurationRejectsWebhook() {
        assertThat(controller.webhook(payload("payment_intent.succeeded"),"fixture").getStatusCode().value()).isEqualTo(503);
        noEffects();
    }
    @Test
    void missingSignatureRejectsWebhook() {
        configured();
        assertThat(controller.webhook(payload("payment_intent.succeeded"),null).getStatusCode().value()).isEqualTo(400);
        noEffects();
    }
    @Test
    void tamperedPayloadRejectsWebhook() throws Exception {
        configured();
        String body=payload("payment_intent.succeeded");
        String signed=signature(body,Instant.now().getEpochSecond());
        assertThat(controller.webhook(body.replace("200","999"),signed).getStatusCode().value()).isEqualTo(400);
        noEffects();
    }
    @Test
    void expiredSignatureRejectsWebhook() throws Exception {
        configured();
        String body=payload("payment_intent.succeeded");
        assertThat(controller.webhook(body,signature(body,Instant.now().minusSeconds(3600).getEpochSecond())).getStatusCode().value()).isEqualTo(400);
        noEffects();
    }
    @Test
    void signedSuccessIsAppliedOnceAndLaterFailureDoesNotUndoPayment() throws Exception {
        configured();
        ReflectionTestUtils.setField(settlement,"bookingFeeCents",200L);
        var appointment=new Appointment();
        ReflectionTestUtils.setField(appointment,"id",1L);
        appointment.setPatient(new Patient());
        appointment.setStatus("PENDING_PAYMENT");
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));
        when(invoices.findByAppointmentOrderByIssuedAtDesc(appointment)).thenReturn(List.of());
        String body=payload("payment_intent.succeeded");
        String signed=signature(body,Instant.now().getEpochSecond());
        assertThat(controller.webhook(body,signed).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(body,signed).getStatusCode().value()).isEqualTo(200);
        String failed=payload("payment_intent.payment_failed");
        assertThat(controller.webhook(failed,signature(failed,Instant.now().getEpochSecond())).getStatusCode().value()).isEqualTo(200);
        assertThat(appointment.isBookingFeePaid()).isTrue();
        assertThat(appointment.getStripePaymentStatus()).isEqualTo("PAID");
        assertThat(appointment.getStatus()).isEqualTo("PENDING");
        verify(invoices,times(1)).save(any());
        verify(notifications,times(1)).notifyPaymentSuccess(appointment);
        verify(appointments,times(1)).save(appointment);
    }
    @Test
    void lateSignedPaymentKeepsCancellationAndRecordsReviewOnce() throws Exception {
        configured();
        var appointment=new Appointment();
        ReflectionTestUtils.setField(appointment,"id",1L);
        appointment.setPatient(new Patient());appointment.setStatus("CANCELLED");
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));
        String body=payload("payment_intent.succeeded");
        String signed=signature(body,Instant.now().getEpochSecond());
        controller.webhook(body,signed);controller.webhook(body,signed);
        assertThat(appointment.getStatus()).isEqualTo("CANCELLED");
        assertThat(appointment.getStripePaymentStatus()).isEqualTo("PAID_REVIEW_REQUIRED");
        assertThat(appointment.isBookingFeePaid()).isTrue();
        verify(invoices,times(1)).save(any());
        verifyNoInteractions(notifications);
    }
}
