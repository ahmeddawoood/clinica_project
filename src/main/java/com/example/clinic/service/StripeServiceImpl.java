package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class StripeServiceImpl implements StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.US);

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.booking-fee-cents:200}")
    private long bookingFeeCents;

    @Value("${stripe.currency:usd}")
    private String currency;

    private boolean stripeEnabled;

    @PostConstruct
    public void init() {
        String key = secretKey == null ? "" : secretKey.trim();
        boolean placeholder = key.contains("replace_me")
                || key.endsWith("_xxx")
                || key.endsWith("xxxxx");
        boolean valid = !key.isBlank()
                && !placeholder
                && (key.startsWith("sk_test_") || key.startsWith("sk_live_"));

        if (!valid) {
            stripeEnabled = false;
            log.warn("Stripe disabled: missing, placeholder, or invalid secret key. Set a real STRIPE_SECRET_KEY.");
            return;
        }

        Stripe.apiKey = key;
        stripeEnabled = true;
        log.info("Stripe initialized (mode={})", key.startsWith("sk_test_") ? "TEST" : "LIVE");
    }

    @Override
    public Session createCheckoutSession(Appointment appointment,
                                         String successUrl,
                                         String cancelUrl) throws StripeException {
        return createCheckoutSession(appointment, successUrl, cancelUrl, null);
    }

    @Override
    public Session createCheckoutSession(Appointment appointment,
                                         String successUrl,
                                         String cancelUrl,
                                         String idempotencyKey) throws StripeException {
        ensureEnabled();

        String currencyCode = (currency == null || currency.isBlank())
                ? "usd"
                : currency.trim().toLowerCase(Locale.ROOT);

        String doctorName = "Dr. " + appointment.getDoctor().getFullName();
        String specialty = appointment.getDoctor().getSpecialty();
        String dateTime = appointment.getAppointmentDate().format(DATE_FMT);
        String patientEmail = appointment.getPatient().getUser().getEmail();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(patientEmail)
                .setClientReferenceId(String.valueOf(appointment.getId()))
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currencyCode)
                                .setUnitAmount(bookingFeeCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Consultation booking fee")
                                        .setDescription(doctorName + " - " + specialty + " - " + dateTime)
                                        .build())
                                .build())
                        .build())
                .putMetadata("appointmentId", String.valueOf(appointment.getId()))
                .putMetadata("patientEmail", patientEmail)
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata("appointmentId", String.valueOf(appointment.getId()))
                                .putMetadata("patientEmail", patientEmail)
                                .setDescription("Appointment booking fee #" + appointment.getId())
                                .build()
                )
                .build();

        Session session;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            session = Session.create(params, requestOptions);
        } else {
            session = Session.create(params);
        }

        log.info("Stripe session created: id={} appointmentId={} amount={} cents patient={}",
                session.getId(), appointment.getId(), bookingFeeCents, patientEmail);
        return session;
    }

    @Override
    public PaymentIntent createPaymentIntent(Appointment appointment, String idempotencyKey) throws StripeException {
        ensureEnabled();

        String currencyCode = (currency == null || currency.isBlank())
                ? "usd"
                : currency.trim().toLowerCase(Locale.ROOT);

        String patientEmail = appointment.getPatient().getUser().getEmail();
        String doctorName = "Dr. " + appointment.getDoctor().getFullName();
        String specialty = appointment.getDoctor().getSpecialty();
        String description = doctorName + " - " + specialty + " - appointment #" + appointment.getId();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(bookingFeeCents)
                .setCurrency(currencyCode)
                .setDescription(description)
                .putMetadata("appointmentId", String.valueOf(appointment.getId()))
                .putMetadata("patientEmail", patientEmail)
                .putMetadata("appointmentDescription", description)
                .addPaymentMethodType("card")
                .build();

        PaymentIntent intent;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RequestOptions requestOptions = RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();
            intent = PaymentIntent.create(params, requestOptions);
        } else {
            intent = PaymentIntent.create(params);
        }

        log.info("Stripe payment intent created: id={} appointmentId={} amount={} cents patient={}",
                intent.getId(), appointment.getId(), bookingFeeCents, patientEmail);
        return intent;
    }

    @Override
    public void refund(String paymentIntentId) throws StripeException {
        ensureEnabled();

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("refund:" + paymentIntentId)
                .build();

        Refund refund = Refund.create(params, requestOptions);
        log.info("Stripe refund requested: refundId={} pi={} status={}",
                refund.getId(), paymentIntentId, refund.getStatus());
    }

    @Override
    public Session retrieveSession(String sessionId) throws StripeException {
        ensureEnabled();
        return Session.retrieve(sessionId, SessionRetrieveParams.builder().build(), null);
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        ensureEnabled();
        return PaymentIntent.retrieve(paymentIntentId);
    }

    @Override
    public boolean isEnabled() {
        return stripeEnabled;
    }

    private void ensureEnabled() {
        if (!stripeEnabled) {
            throw new IllegalStateException("Stripe nu este configurat. Setați STRIPE_SECRET_KEY.");
        }
    }
}
