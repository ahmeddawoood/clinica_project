package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Invoice;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.service.ActivityLogService;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.PatientService;
import com.example.clinic.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final DateTimeFormatter IDEMPOTENCY_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PatientService patientService;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ActivityLogService activityLogService;

    @Value("${stripe.booking-fee-cents:200}")
    private long bookingFeeCents;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @Value("${stripe.publishable-key:}")
    private String publishableKey;

    @Value("${payment.coupon-code:ahmad123}")
    private String paymentCouponCode;

    @Value("${payment.demo-fallback-enabled:false}")
    private boolean demoFallbackEnabled;

    public PaymentController(AppointmentRepository appointmentRepository,
                             InvoiceRepository invoiceRepository,
                             PatientService patientService,
                             StripeService stripeService,
                             NotificationService notificationService,
                             AuditLogService auditLogService,
                             ActivityLogService activityLogService) {
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.patientService = patientService;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.activityLogService = activityLogService;
    }

    @PostMapping("/create-checkout-session")
    @Transactional
    public String createCheckoutSession(@RequestParam("appointmentId") Long appointmentId,
                                        Principal principal,
                                        HttpServletRequest request,
                                        RedirectAttributes ra) {
        Appointment appointment = loadOwnedAppointment(appointmentId, principal.getName());

        if (!stripeService.isEnabled()) {
            if (demoFallbackEnabled) {
                log.warn("Stripe disabled. Completing appointment {} in local demo fallback mode.", appointmentId);
                return completeDemoPayment(appointment, principal.getName(), ra);
            }
            ra.addFlashAttribute("error", "Plata online este temporar indisponibilă.");
            return "redirect:/patient/appointments/" + appointmentId + "/pay";
        }

        if ("CANCELLED".equals(appointment.getStatus())) {
            ra.addFlashAttribute("error", "Această programare este anulată și nu mai poate fi plătită.");
            return "redirect:/patient/dashboard";
        }

        if (appointment.isBookingFeePaid()) {
            return "redirect:/payment-success?appointmentId=" + appointmentId;
        }

        try {
            if (appointment.getStripeSessionId() != null && !appointment.getStripeSessionId().isBlank()) {
                Session existingSession = stripeService.retrieveSession(appointment.getStripeSessionId());
                if ("open".equals(existingSession.getStatus()) && existingSession.getUrl() != null) {
                    log.info("Reusing open Checkout Session: appointmentId={} sessionId={}",
                            appointmentId, existingSession.getId());
                    return "redirect:" + existingSession.getUrl();
                }
            }
        } catch (Exception e) {
            log.info("Existing checkout session is not reusable for appointment {}: {}",
                    appointmentId, e.getMessage());
        }

        try {
            String baseUrl = baseUrl(request);
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
            return "redirect:" + session.getUrl();

        } catch (IllegalStateException e) {
            log.warn("Stripe config error while creating checkout session: {}", e.getMessage());
            ra.addFlashAttribute("error", "Eroare de configurare plată. Contactați suportul.");
            return "redirect:/patient/appointments/" + appointmentId + "/pay";
        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed for appointment {}: {}",
                    appointmentId, e.getMessage());
            ra.addFlashAttribute("error", "Nu s-a putut iniția plata. Vă rugăm să încercați din nou.");
            return "redirect:/patient/appointments/" + appointmentId + "/pay";
        }
    }

    @PostMapping(value = "/create-payment-intent", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createPaymentIntent(@RequestParam("appointmentId") Long appointmentId,
                                                                   Principal principal) {
        Appointment appointment = loadOwnedAppointment(appointmentId, principal.getName());

        if (!stripeService.isEnabled()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Stripe nu este configurat.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(payload);
        }

        if (appointment.isBookingFeePaid()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Aceasta programare este deja achitata.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
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
            return ResponseEntity.ok(payload);
        } catch (IllegalStateException e) {
            log.warn("Stripe config error while creating payment intent: {}", e.getMessage());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Stripe nu este configurat corect.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(payload);
        } catch (StripeException e) {
            log.error("Stripe payment intent creation failed for appointment {}: {}",
                    appointmentId, e.getMessage());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Nu s-a putut initia plata.");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(payload);
        }
    }

    @PostMapping(value = "/apply-payment-coupon", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyPaymentCoupon(@RequestParam("appointmentId") Long appointmentId,
                                                                  @RequestParam("couponCode") String couponCode,
                                                                  Principal principal) {
        Appointment appointment = loadOwnedAppointment(appointmentId, principal.getName());

        if (appointment.isBookingFeePaid()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Aceasta programare este deja achitata.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
        }

        String entered = couponCode == null ? "" : couponCode.trim();
        String expected = paymentCouponCode == null ? "" : paymentCouponCode.trim();
        if (entered.isEmpty() || expected.isEmpty() || !expected.equalsIgnoreCase(entered)) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Codul de cupon este invalid.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
        }

        markPaidViaCoupon(appointment, entered);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("redirectUrl", "/payment-success?appointmentId=" + appointmentId);
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/payment-success")
    @Transactional
    public String paymentSuccess(@RequestParam("appointmentId") Long appointmentId,
                                 @RequestParam(name = "session_id", required = false) String sessionId,
                                 @RequestParam(name = "payment_intent", required = false) String paymentIntentId,
                                 Principal principal,
                                 Model model) {
        Appointment appointment = loadOwnedAppointment(appointmentId, principal.getName());

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

        if (!appointment.isBookingFeePaid()) {
            return "redirect:/payment-cancel?appointmentId=" + appointmentId;
        }

        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patientService.getPatientByEmail(principal.getName()));
        return "patient/payment-success";
    }

    @GetMapping("/payment-cancel")
    public String paymentCancel(@RequestParam("appointmentId") Long appointmentId,
                                Principal principal,
                                Model model) {
        Appointment appointment = loadOwnedAppointment(appointmentId, principal.getName());
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patientService.getPatientByEmail(principal.getName()));
        return "patient/payment-failed";
    }

    private void markPaidIfNeeded(Appointment appointment, Session session) {
        if (appointment.isBookingFeePaid()) {
            return;
        }

        appointment.setBookingFeePaid(true);
        appointment.setStatus("PENDING");
        appointment.setStripePaymentStatus("PAID");
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
        notificationService.notifyPaymentSuccess(appointment);
        auditLogService.log("payment-success-page", "PAYMENT_COMPLETED",
                "Payment confirmed for appointment #" + appointment.getId());
        activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED",
                "Payment confirmed via Stripe Checkout");
    }

    private void markPaidIfNeeded(Appointment appointment, PaymentIntent paymentIntent) {
        if (appointment.isBookingFeePaid()) {
            return;
        }

        appointment.setBookingFeePaid(true);
        appointment.setStatus("PENDING");
        appointment.setStripePaymentStatus("PAID");
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
        notificationService.notifyPaymentSuccess(appointment);
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
        notificationService.notifyPaymentSuccess(appointment);
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

    private String completeDemoPayment(Appointment appointment, String actorEmail, RedirectAttributes ra) {
        if (appointment.isBookingFeePaid()) {
            return "redirect:/payment-success?appointmentId=" + appointment.getId();
        }

        String demoPi = "local_demo_pi_" + UUID.randomUUID().toString().replace("-", "");
        String demoSession = "local_demo_cs_" + UUID.randomUUID().toString().replace("-", "");

        appointment.setBookingFeePaid(true);
        appointment.setStatus("PENDING");
        appointment.setStripePaymentStatus("PAID");
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
        notificationService.notifyPaymentSuccess(appointment);
        auditLogService.log(actorEmail, "PAYMENT_COMPLETED_DEMO",
                "Local demo payment completed for appointment #" + appointment.getId());
        activityLogService.saveLog(appointment.getId(), "PAYMENT_COMPLETED_DEMO",
                "Payment completed in local demo fallback mode");

        ra.addFlashAttribute("info",
                "Plată finalizată în modul demo local. Adăugați chei Stripe reale pentru plăți live.");
        return "redirect:/payment-success?appointmentId=" + appointment.getId();
    }

    private Appointment loadOwnedAppointment(Long appointmentId, String email) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        String ownerEmail = appointment.getPatient().getUser().getEmail();
        if (!ownerEmail.equals(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Nu aveți permisiunea de a accesa această programare.");
        }

        return appointment;
    }

    private String baseUrl(HttpServletRequest request) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.endsWith("/")
                    ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                    : configuredBaseUrl;
        }

        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
