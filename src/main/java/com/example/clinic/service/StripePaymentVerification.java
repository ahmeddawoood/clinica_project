package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Map;

/** Shared acceptance policy for the clinic's configured fixed booking fee. */
@Component
public class StripePaymentVerification {
    private final long expectedAmount;
    private final String expectedCurrency;

    public StripePaymentVerification(@Value("${stripe.booking-fee-cents:200}") long amount,
                                     @Value("${stripe.currency:usd}") String currency) {
        this.expectedAmount = amount;
        this.expectedCurrency = currency == null || currency.isBlank() ? "usd"
                : currency.trim().toLowerCase(Locale.ROOT);
    }

    public void verify(Appointment appointment, PaymentIntent intent) {
        verifyOwner(appointment, intent.getMetadata());
        verifyAmountAndCurrency(intent.getAmount(), intent.getCurrency());
        if (!"succeeded".equals(intent.getStatus()) || intent.getAmountReceived() == null
                || intent.getAmountReceived() != expectedAmount) {
            throw new RejectedPaymentException();
        }
    }

    public void verify(Appointment appointment, Session session) {
        verifyOwner(appointment, session.getMetadata());
        verifyAmountAndCurrency(session.getAmountTotal(), session.getCurrency());
        if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus())) {
            throw new RejectedPaymentException();
        }
    }

    private void verifyOwner(Appointment appointment, Map<String, String> metadata) {
        if (appointment.getId() == null || metadata == null
                || !appointment.getId().toString().equals(metadata.get("appointmentId"))) {
            throw new RejectedPaymentException();
        }
    }

    private void verifyAmountAndCurrency(Long amount, String currency) {
        if (expectedAmount <= 0 || amount == null || amount != expectedAmount
                || currency == null || !expectedCurrency.equals(currency.toLowerCase(Locale.ROOT))) {
            throw new RejectedPaymentException();
        }
    }

    public static class RejectedPaymentException extends RuntimeException {
        public RejectedPaymentException() { super("Payment details do not match the appointment booking fee"); }
    }
}
