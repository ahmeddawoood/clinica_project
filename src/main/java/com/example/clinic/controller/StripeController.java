package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Invoice;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.service.ActivityLogService;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.NotificationService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@Tag(name = "Plăți Stripe",
     description = "Webhook Stripe pentru procesarea evenimentelor de plată și rambursare")
public class StripeController {

    private static final Logger log = LoggerFactory.getLogger(StripeController.class);

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.booking-fee-cents:200}")
    private long bookingFeeCents;

    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ActivityLogService activityLogService;

    public StripeController(AppointmentRepository appointmentRepository,
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

    @PostMapping("/stripe/webhook")
    @Transactional
    @Operation(
        summary = "Webhook Stripe",
        description = """
                Endpoint de callback pentru evenimentele Stripe.
                Procesează automat:
                - `checkout.session.completed` - asociază sesiunea de plată cu programarea
                - `payment_intent.succeeded` - marchează programarea ca plătită, creează factură
                - `payment_intent.payment_failed` - înregistrează eșecul plății
                - `charge.refunded` - finalizează rambursarea în baza de date
                Configurat prin variabila de mediu `STRIPE_WEBHOOK_SECRET`.
                """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eveniment procesat cu succes"),
        @ApiResponse(responseCode = "400", description = "Semnătură invalidă sau payload malformat",
            content = @Content(mediaType = "text/plain")),
        @ApiResponse(responseCode = "503", description = "Webhook secret neconficgurat",
            content = @Content(mediaType = "text/plain"))
    })
    public ResponseEntity<String> webhook(
            @Parameter(description = "Payload JSON raw de la Stripe", required = true)
            @RequestBody String payload,
            @Parameter(description = "Header de semnătură Stripe (ex. `t=...,v1=...`)", required = true)
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Stripe webhook rejected: STRIPE_WEBHOOK_SECRET is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook secret not configured");
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Stripe webhook rejected: missing Stripe-Signature header");
            return ResponseEntity.badRequest().body("Missing signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (RuntimeException e) {
            log.warn("Stripe webhook payload invalid: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        log.info("Stripe webhook received: type={} eventId={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed"  -> handleCheckoutSessionCompleted(event);
            case "payment_intent.succeeded"    -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "charge.refunded"             -> handleChargeRefunded(event);
            default -> log.debug("Stripe event ignored: {}", event.getType());
        }

        return ResponseEntity.ok("OK");
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
            appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
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
            appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
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
                notificationService.notifyPaymentSuccess(appointment);

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
            appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
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
        appointment.setBookingFeePaid(true);
        appointment.setStatus("PENDING");
        appointment.setStripePaymentStatus("PAID");
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
