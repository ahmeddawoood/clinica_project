package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.AppointmentLifecycle;
import com.example.clinic.domain.Invoice;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Applies verified Stripe events within one transactional write boundary. */
@Service
public class StripeWebhookService {
    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    @Value("${stripe.booking-fee-cents:200}")
    private long bookingFeeCents;

    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ActivityLogService activityLogService;

    public StripeWebhookService(AppointmentRepository appointmentRepository,
                            InvoiceRepository invoiceRepository,
                            NotificationService notificationService,
                            AuditLogService auditLogService,
                            ActivityLogService activityLogService) {
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public void processVerifiedEvent(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed"  -> handleCheckoutSessionCompleted(event);
            case "payment_intent.succeeded"    -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "charge.refunded"             -> handleChargeRefunded(event);
            default -> log.debug("Stripe event ignored: {}", event.getType());
        }

    }

    private void handleCheckoutSessionCompleted(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresentOrElse(data -> {
            Session session = (Session) data;
            String appointmentIdStr = session.getMetadata() != null
                    ? session.getMetadata().get("appointmentId") : null;
            if (appointmentIdStr == null || appointmentIdStr.isBlank()) {
                log.warn("checkout.session.completed missing appointmentId metadata (session={})", session.getId());
                return;
            }
            Long appointmentId = Long.parseLong(appointmentIdStr);
            appointmentRepository.findByIdForUpdate(appointmentId).ifPresent(appointment -> {
                if (appointment.getStripeSessionId() == null) {
                    appointment.setStripeSessionId(session.getId());
                    appointmentRepository.save(appointment);
                }
                log.info("checkout.session.completed: appointmentId={} sessionId={}",
                        appointmentId, session.getId());
            });
        }, () -> log.warn("checkout.session.completed has no data object"));
    }

    private void handlePaymentIntentSucceeded(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresentOrElse(data -> {
            PaymentIntent paymentIntent = (PaymentIntent) data;
            String appointmentIdText = paymentIntent.getMetadata() != null
                    ? paymentIntent.getMetadata().get("appointmentId")
                    : null;
            if (appointmentIdText == null || appointmentIdText.isBlank()) {
                log.warn("payment_intent.succeeded missing appointmentId metadata (pi={})", paymentIntent.getId());
                return;
            }

            Long appointmentId = Long.parseLong(appointmentIdText);
            appointmentRepository.findByIdForUpdate(appointmentId).ifPresent(appointment -> {
                if (appointment.isBookingFeePaid()) {
                    log.info("Appointment {} already paid. Duplicate succeeded event ignored.", appointmentId);
                    return;
                }

                markAppointmentAsPaid(appointment, paymentIntent);
                ensurePaidInvoice(appointment, paymentIntent.getId(), appointment.getStripeSessionId());

                auditLogService.log("stripe-webhook", "PAYMENT_COMPLETED",
                        "Payment confirmed for appointment #" + appointment.getId()
                                + " | PI: " + paymentIntent.getId());

                activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED",
                        "Payment confirmed by Stripe webhook");
                if (!AppointmentLifecycle.isClosed(appointment)) notificationService.notifyPaymentSuccess(appointment);

                log.info("payment_intent.succeeded applied for appointment {} (pi={})",
                        appointment.getId(), paymentIntent.getId());
            });
        }, () -> log.warn("payment_intent.succeeded event has no data object"));
    }

    private void handlePaymentIntentFailed(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresentOrElse(data -> {
            PaymentIntent paymentIntent = (PaymentIntent) data;
            String appointmentIdText = paymentIntent.getMetadata() != null
                    ? paymentIntent.getMetadata().get("appointmentId")
                    : null;
            if (appointmentIdText == null || appointmentIdText.isBlank()) {
                log.warn("payment_intent.payment_failed missing appointmentId metadata (pi={})", paymentIntent.getId());
                return;
            }

            Long appointmentId = Long.parseLong(appointmentIdText);
            appointmentRepository.findByIdForUpdate(appointmentId).ifPresent(appointment -> {
                if (appointment.isBookingFeePaid()) {
                    log.info("Failed event ignored because appointment {} is already paid", appointmentId);
                    return;
                }

                appointment.setStripePaymentStatus("FAILED");
                appointmentRepository.save(appointment);

                String failureReason = paymentIntent.getLastPaymentError() != null
                        ? paymentIntent.getLastPaymentError().getMessage()
                        : "payment failed";

                auditLogService.log("stripe-webhook", "PAYMENT_FAILED",
                        "Payment failed for appointment #" + appointment.getId()
                                + " | PI: " + paymentIntent.getId()
                                + " | Reason: " + failureReason);

                activityLogService.saveLog(appointment.getId(), "PAYMENT_FAILED",
                        "Payment failed: " + failureReason);

                log.warn("payment_intent.payment_failed applied for appointment {} (pi={}): {}",
                        appointment.getId(), paymentIntent.getId(), failureReason);
            });
        }, () -> log.warn("payment_intent.payment_failed event has no data object"));
    }

    private void handleChargeRefunded(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresentOrElse(data -> {
            Charge charge = (Charge) data;
            String paymentIntentId = charge.getPaymentIntent();
            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                return;
            }

            appointmentRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(appointment -> {
                if (appointment.isBookingFeeRefunded()) {
                    return;
                }

                String refundId = null;
                if (charge.getRefunds() != null && !charge.getRefunds().getData().isEmpty()) {
                    refundId = charge.getRefunds().getData().get(0).getId();
                }

                appointment.setBookingFeeRefunded(true);
                appointment.setStripePaymentStatus("REFUNDED");
                appointment.setStripeRefundId(refundId);
                appointmentRepository.save(appointment);

                final String finalRefundId = refundId;
                invoiceRepository.findByAppointmentOrderByIssuedAtDesc(appointment)
                        .stream()
                        .findFirst()
                        .ifPresent(invoice -> {
                            invoice.setStatus("REFUNDED");
                            invoice.setStripeRefundId(finalRefundId);
                            invoiceRepository.save(invoice);
                        });

                auditLogService.log("stripe-webhook", "REFUND_COMPLETED",
                        "Refund confirmed for appointment #" + appointment.getId()
                                + " | Refund: " + refundId);

                activityLogService.saveLog(appointment.getId(), "REFUND_COMPLETED",
                        "Refund confirmed by Stripe");
                notificationService.notifyRefund(appointment);
            });
        }, () -> log.warn("charge.refunded event has no data object"));
    }

    private void markAppointmentAsPaid(Appointment appointment, PaymentIntent paymentIntent) {
        AppointmentLifecycle.recordSuccessfulPayment(appointment);
        appointment.setStripePaymentIntentId(paymentIntent.getId());
        appointment.setStripeAmountCents(paymentIntent.getAmount());
        appointment.setStripeCurrency(paymentIntent.getCurrency());
        appointment.setStripePaidAt(LocalDateTime.now());
        appointmentRepository.save(appointment);
    }

    private void ensurePaidInvoice(Appointment appointment, String paymentIntentId, String sessionId) {
        List<Invoice> invoices = invoiceRepository.findByAppointmentOrderByIssuedAtDesc(appointment);
        boolean exists = invoices.stream()
                .anyMatch(inv -> "PAID".equals(inv.getStatus())
                        && paymentIntentId != null
                        && paymentIntentId.equals(inv.getStripePaymentIntentId()));
        if (exists) {
            return;
        }

        Invoice invoice = new Invoice();
        invoice.setPatient(appointment.getPatient());
        invoice.setAppointment(appointment);
        invoice.setAmount(new BigDecimal(bookingFeeCents).movePointLeft(2));
        invoice.setDescription("Appointment booking fee");
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaymentMethod("stripe_checkout");
        invoice.setStripePaymentIntentId(paymentIntentId);
        invoice.setStripeSessionId(sessionId);
        invoiceRepository.save(invoice);
    }
}
