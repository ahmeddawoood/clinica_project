package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class StripePaymentVerificationTest {
    @Test void missingFinancialDataAndUncapturedFundsAreRejected() {
        var appointment = new Appointment(); ReflectionTestUtils.setField(appointment, "id", 1L);
        var policy = new StripePaymentVerification(200, "usd");
        var intent = new PaymentIntent(); intent.setMetadata(Map.of("appointmentId", "1")); intent.setStatus("succeeded");
        assertThatThrownBy(() -> policy.verify(appointment, intent)).isInstanceOf(StripePaymentVerification.RejectedPaymentException.class);
        intent.setAmount(200L); intent.setCurrency("usd");
        assertThatThrownBy(() -> policy.verify(appointment, intent)).isInstanceOf(StripePaymentVerification.RejectedPaymentException.class);
        intent.setAmountReceived(200L); intent.setStatus("requires_capture");
        assertThatThrownBy(() -> policy.verify(appointment, intent)).isInstanceOf(StripePaymentVerification.RejectedPaymentException.class);
        intent.setStatus("succeeded"); intent.setCurrency(null);
        assertThatThrownBy(() -> policy.verify(appointment, intent)).isInstanceOf(StripePaymentVerification.RejectedPaymentException.class);
        intent.setCurrency("USD");
        assertThatCode(() -> policy.verify(appointment, intent)).doesNotThrowAnyException();
    }
}
