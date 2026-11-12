package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.AppointmentLifecycle;
import com.example.clinic.domain.Invoice;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@org.springframework.stereotype.Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);
    private static final DateTimeFormatter IDEMPOTENCY_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ActivityLogService activityLogService;

    @Value("${stripe.booking-fee-cents:200}")
    private long bookingFeeCents;

    @Value("${payment.coupon-code:ahmad123}")
    private String paymentCouponCode;

    @Value("${payment.demo-fallback-enabled:false}")
    private boolean demoFallbackEnabled;

    public PaymentApplicationService(AppointmentRepository appointmentRepository,
                             InvoiceRepository invoiceRepository,
                             StripeService stripeService,
                             NotificationService notificationService,
                             AuditLogService auditLogService,
                             ActivityLogService activityLogService) {
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.activityLogService = activityLogService;
    }

    public enum CheckoutStatus { CLOSED, UNAVAILABLE, CONFIG_ERROR, PROVIDER_FAILURE, PAID, DEMO_PAID, READY }
    public record CheckoutResult(CheckoutStatus status, String url) {
        static CheckoutResult of(CheckoutStatus status) { return new CheckoutResult(status, null); }
    }
    public enum PaymentStatus { SUCCESS, CONFLICT, UNAVAILABLE, INVALID, PROVIDER_FAILURE }
    public record PaymentResult(PaymentStatus status, Map<String, Object> data) {}
    public static class PaymentMismatchException extends RuntimeException {
        public PaymentMismatchException() { super("Payment does not belong to this appointment"); }
    }

    @Transactional
    public CheckoutResult createCheckoutSession(Long appointmentId, String email, String baseUrl) {
        Appointment appointment = loadOwnedAppointment(appointmentId, email);

        if (isClosed(appointment)) {
            return CheckoutResult.of(CheckoutStatus.CLOSED);
        }

        if (!stripeService.isEnabled()) {
            if (demoFallbackEnabled) {
                log.warn("Stripe disabled. Completing appointment {} in local demo fallback mode.", appointmentId);
                return completeDemoPayment(appointment, email);
            }
            return CheckoutResult.of(CheckoutStatus.UNAVAILABLE);
        }

        if (appointment.isBookingFeePaid()) {
            return CheckoutResult.of(CheckoutStatus.PAID);
        }

        try {
            if (appointment.getStripeSessionId() != null && !appointment.getStripeSessionId().isBlank()) {
                Session existingSession = stripeService.retrieveSession(appointment.getStripeSessionId());
                if ("open".equals(existingSession.getStatus()) && existingSession.getUrl() != null) {
                    log.info("Reusing open Checkout Session: appointmentId={} sessionId={}",
                            appointmentId, existingSession.getId());
                    return new CheckoutResult(CheckoutStatus.READY, existingSession.getUrl());
                }
            }
        } catch (Exception e) {
            log.info("Existing checkout session is not reusable for appointment {}: {}",
                    appointmentId, e.getMessage());
        }

        try {
            String successUrl = baseUrl + "/payment-success?appointmentId=" + appointmentId
                    + "&session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = baseUrl + "/payment-cancel?appointmentId=" + appointmentId;

            String idempotencyKey = "checkout-" + appointmentId + "-"
                    + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).format(IDEMPOTENCY_FMT);

            Session session = stripeService.createCheckoutSession(
                    appointment,
                    successUrl,
                    cancelUrl,
                    idempotencyKey
            );

            appointment.setStripeSessionId(session.getId());
            appointment.setStripePaymentStatus("PENDING");
            appointmentRepository.save(appointment);

            log.info("Checkout Session created: appointmentId={} sessionId={}",
                    appointmentId, session.getId());
            return new CheckoutResult(CheckoutStatus.READY, session.getUrl());

        } catch (IllegalStateException e) {
            log.warn("Stripe config error while creating checkout session: {}", e.getMessage());
            return CheckoutResult.of(CheckoutStatus.CONFIG_ERROR);
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed for appointment {}: {}",
                    appointmentId, e.getMessage());
            return CheckoutResult.of(CheckoutStatus.PROVIDER_FAILURE);
        }
    }

    @Transactional
    public PaymentResult createPaymentIntent(Long appointmentId, String email) {
        Appointment appointment = loadOwnedAppointment(appointmentId, email);

        if (isClosed(appointment)) {
            return new PaymentResult(PaymentStatus.CONFLICT, Map.of("error", "Această programare este închisă și nu mai poate fi plătită."));
        }

        if (!stripeService.isEnabled()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Stripe nu este configurat.");
            return new PaymentResult(PaymentStatus.UNAVAILABLE, payload);
        }

        if (appointment.isBookingFeePaid()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Aceasta programare este deja achitata.");
            return new PaymentResult(PaymentStatus.CONFLICT, payload);
        }

        try {
            String idempotencyKey = "pi-" + appointmentId + "-"
                    + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).format(IDEMPOTENCY_FMT);

            PaymentIntent paymentIntent = stripeService.createPaymentIntent(appointment, idempotencyKey);
            appointment.setStripePaymentIntentId(paymentIntent.getId());
            appointment.setStripePaymentStatus("REQUIRES_PAYMENT_METHOD");
            appointmentRepository.save(appointment);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientSecret", paymentIntent.getClientSecret());
            payload.put("paymentIntentId", paymentIntent.getId());
            payload.put("amount", paymentIntent.getAmount());
            payload.put("currency", paymentIntent.getCurrency());
            return new PaymentResult(PaymentStatus.SUCCESS, payload);
        } catch (IllegalStateException e) {
            log.warn("Stripe config error while creating payment intent: {}", e.getMessage());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Stripe nu este configurat corect.");
            return new PaymentResult(PaymentStatus.UNAVAILABLE, payload);
        } catch (StripeException e) {
            log.error("Stripe payment intent creation failed for appointment {}: {}",
                    appointmentId, e.getMessage());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Nu s-a putut initia plata.");
            return new PaymentResult(PaymentStatus.PROVIDER_FAILURE, payload);
        }
    }

    @Transactional
    public PaymentResult applyPaymentCoupon(Long appointmentId, String couponCode, String email) {
        Appointment appointment = loadOwnedAppointment(appointmentId, email);

        if (isClosed(appointment)) {
            return new PaymentResult(PaymentStatus.CONFLICT, Map.of("error", "Această programare este închisă și nu mai poate fi plătită."));
        }

        if (appointment.isBookingFeePaid()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Aceasta programare este deja achitata.");
            return new PaymentResult(PaymentStatus.CONFLICT, payload);
        }

        String entered = couponCode == null ? "" : couponCode.trim();
        String expected = paymentCouponCode == null ? "" : paymentCouponCode.trim();
        if (entered.isEmpty() || expected.isEmpty() || !expected.equalsIgnoreCase(entered)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Codul de cupon este invalid.");
            return new PaymentResult(PaymentStatus.INVALID, payload);
        }

        markPaidViaCoupon(appointment, entered);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return new PaymentResult(PaymentStatus.SUCCESS, payload);
    }

    @Transactional
    public Appointment verifyPaymentReturn(Long appointmentId, String sessionId, String paymentIntentId, String email) {
        Appointment appointment = loadOwnedAppointment(appointmentId, email);

        if (paymentIntentId != null && !paymentIntentId.isBlank() && !appointment.isBookingFeePaid()) {
            try {
                PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(paymentIntentId);
                if ("succeeded".equals(paymentIntent.getStatus())) {
                    markPaidIfNeeded(appointment, paymentIntent);
                }
            } catch (StripeException e) {
                log.warn("Could not verify PaymentIntent {} for appointment {}: {}",
                        paymentIntentId, appointmentId, e.getMessage());
            }
        }

        if (sessionId != null && !sessionId.isBlank() && !appointment.isBookingFeePaid()) {
            try {
                Session session = stripeService.retrieveSession(sessionId);
                if ("complete".equals(session.getStatus()) && "paid".equals(session.getPaymentStatus())) {
                    markPaidIfNeeded(appointment, session);
                }
            } catch (StripeException e) {
                log.warn("Could not verify Checkout Session {} for appointment {}: {}",
                        sessionId, appointmentId, e.getMessage());
            }
        }

        return appointment;
    }

    @Transactional
    public Appointment getOwnedAppointment(Long appointmentId, String email) {
        return loadOwnedAppointment(appointmentId, email);
    }

    private void markPaidIfNeeded(Appointment appointment, Session session) {
        if (session.getMetadata() == null || !String.valueOf(appointment.getId()).equals(
                session.getMetadata().get("appointmentId"))) {
            throw new PaymentMismatchException();
        }
        if (appointment.isBookingFeePaid()) {
            return;
        }

        AppointmentLifecycle.recordSuccessfulPayment(appointment);
        appointment.setStripePaymentIntentId(session.getPaymentIntent());
        appointment.setStripeSessionId(session.getId());
        appointment.setStripeAmountCents(session.getAmountTotal());
        appointment.setStripeCurrency(session.getCurrency());
        appointment.setStripePaidAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        ensurePaidInvoice(appointment,
                BigDecimal.valueOf(bookingFeeCents).movePointLeft(2),
                "stripe_checkout",
                session.getPaymentIntent(),
                session.getId());
        if (!AppointmentLifecycle.isClosed(appointment)) notificationService.notifyPaymentSuccess(appointment);
        auditLogService.log("payment-success-page", "PAYMENT_COMPLETED",
                "Payment confirmed for appointment #" + appointment.getId());
        activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED",
                "Payment confirmed via Stripe Checkout");
    }

    private void markPaidIfNeeded(Appointment appointment, PaymentIntent paymentIntent) {
        if (paymentIntent.getMetadata() == null || !String.valueOf(appointment.getId()).equals(
                paymentIntent.getMetadata().get("appointmentId"))) {
            throw new PaymentMismatchException();
        }
        if (appointment.isBookingFeePaid()) {
            return;
        }

        AppointmentLifecycle.recordSuccessfulPayment(appointment);
        appointment.setStripePaymentIntentId(paymentIntent.getId());
        appointment.setStripeAmountCents(paymentIntent.getAmount());
        appointment.setStripeCurrency(paymentIntent.getCurrency());
        appointment.setStripePaidAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        ensurePaidInvoice(appointment,
                BigDecimal.valueOf(bookingFeeCents).movePointLeft(2),
                "stripe_payment_intent",
                paymentIntent.getId(),
                appointment.getStripeSessionId());
        if (!AppointmentLifecycle.isClosed(appointment)) notificationService.notifyPaymentSuccess(appointment);
        auditLogService.log("payment-success-page", "PAYMENT_COMPLETED",
                "Payment confirmed for appointment #" + appointment.getId());
        activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED",
                "Payment confirmed via Stripe PaymentIntent");
    }

    private void markPaidViaCoupon(Appointment appointment, String couponCode) {
        appointment.setBookingFeePaid(true);
        appointment.setStatus("PENDING");
        appointment.setStripePaymentStatus("COUPON");
        appointment.setStripePaymentIntentId(null);
        appointment.setStripeSessionId(null);
        appointment.setStripeAmountCents(0L);
        appointment.setStripeCurrency("usd");
        appointment.setStripePaidAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        ensurePaidInvoice(appointment,
                BigDecimal.ZERO,
                "coupon:" + couponCode,
                null,
                null);
        if (!AppointmentLifecycle.isClosed(appointment)) notificationService.notifyPaymentSuccess(appointment);
        auditLogService.log("coupon-code", "PAYMENT_COMPLETED_COUPON",
                "Coupon applied for appointment #" + appointment.getId() + " | Code: " + couponCode);
        activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED_COUPON",
                "Payment waived by coupon code");
    }

    private void ensurePaidInvoice(Appointment appointment,
                                   BigDecimal amount,
                                   String paymentMethod,
                                   String paymentIntentId,
                                   String sessionId) {
        List<Invoice> invoices = invoiceRepository.findByAppointmentOrderByIssuedAtDesc(appointment);
        boolean exists = invoices.stream()
                .anyMatch(inv -> "PAID".equals(inv.getStatus())
                        && ((paymentIntentId == null && inv.getStripePaymentIntentId() == null)
                        || (paymentIntentId != null && paymentIntentId.equals(inv.getStripePaymentIntentId()))));
        if (exists) {
            return;
        }

        Invoice invoice = new Invoice();
        invoice.setPatient(appointment.getPatient());
        invoice.setAppointment(appointment);
        invoice.setAmount(amount);
        invoice.setDescription("Appointment booking fee");
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaymentMethod(paymentMethod);
        invoice.setStripePaymentIntentId(paymentIntentId);
        invoice.setStripeSessionId(sessionId);
        invoiceRepository.save(invoice);
    }

    private CheckoutResult completeDemoPayment(Appointment appointment, String actorEmail) {
        if (appointment.isBookingFeePaid()) {
            return CheckoutResult.of(CheckoutStatus.PAID);
        }

        String demoPi = "local_demo_pi_" + UUID.randomUUID().toString().replace("-", "");
        String demoSession = "local_demo_cs_" + UUID.randomUUID().toString().replace("-", "");

        AppointmentLifecycle.recordSuccessfulPayment(appointment);
        appointment.setStripePaymentIntentId(demoPi);
        appointment.setStripeSessionId(demoSession);
        appointment.setStripeAmountCents(bookingFeeCents);
        appointment.setStripeCurrency("usd");
        appointment.setStripePaidAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        ensurePaidInvoice(appointment,
                BigDecimal.valueOf(bookingFeeCents).movePointLeft(2),
                "demo",
                demoPi,
                demoSession);
        if (!AppointmentLifecycle.isClosed(appointment)) notificationService.notifyPaymentSuccess(appointment);
        auditLogService.log(actorEmail, "PAYMENT_COMPLETED_DEMO",
                "Local demo payment completed for appointment #" + appointment.getId());
        activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED_DEMO",
                "Payment completed in local demo fallback mode");

        return CheckoutResult.of(CheckoutStatus.DEMO_PAID);
    }

    private static boolean isClosed(Appointment appointment) {
        return AppointmentLifecycle.isClosed(appointment);
    }

    private Appointment loadOwnedAppointment(Long appointmentId, String email) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        String ownerEmail = appointment.getPatient().getUser().getEmail();
        if (!ownerEmail.equals(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Nu aveți permisiunea de a accesa această programare.");
        }

        return appointment;
    }

}
