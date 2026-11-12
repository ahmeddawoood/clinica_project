package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import com.stripe.Stripe;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "stripe.webhook-secret=whsec_transaction_fixture")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StripeSettlementTransactionTest {
    @Autowired StripeController controller;
    @Autowired AppointmentRepository appointments;
    @Autowired InvoiceRepository invoices;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean NotificationService notifications;
    @MockitoBean StripeService stripeService;

    @Test
    void failedSettlementRollsBackAndSignedRetryCreatesOneInvoice() throws Exception {
        // Deliberately no test-level transaction: the service proxy must own rollback.
        Long id = new TransactionTemplate(transactionManager).execute(tx -> {
            var patient = new Patient(); patient.setFirstName("Fixture"); em.persist(patient);
            var doctor = new Doctor(); doctor.setFirstName("Fixture"); em.persist(doctor);
            var appointment = new Appointment(); appointment.setPatient(patient); appointment.setDoctor(doctor);
            appointment.setAppointmentDate(LocalDateTime.now().plusDays(3));
            appointment.setStatus("PENDING_PAYMENT"); em.persist(appointment); em.flush();
            return appointment.getId();
        });
        long invoicesBefore = invoices.count();
        String body = "{\"id\":\"evt_transaction\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
                + "\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_transaction\","
                + "\"object\":\"payment_intent\",\"amount\":200,\"currency\":\"usd\",\"status\":\"succeeded\","
                + "\"metadata\":{\"appointmentId\":\"" + id + "\"}}}}";
        long timestamp = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_transaction_fixture".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));

        doThrow(new IllegalStateException("notification fixture failure")).when(notifications).notifyPaymentSuccess(any());
        assertThatThrownBy(() -> controller.webhook(body, signature)).isInstanceOf(IllegalStateException.class);
        assertThat(appointments.findById(id).orElseThrow().isBookingFeePaid()).isFalse();
        assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(invoices.count()).isEqualTo(invoicesBefore);

        doNothing().when(notifications).notifyPaymentSuccess(any());
        assertThat(controller.webhook(body, signature).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.webhook(body, signature).getStatusCode().value()).isEqualTo(200);
        assertThat(appointments.findById(id).orElseThrow().isBookingFeePaid()).isTrue();
        assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(invoices.count()).isEqualTo(invoicesBefore + 1);
        verify(notifications, times(2)).notifyPaymentSuccess(any()); // failed attempt + successful retry
    }
}
