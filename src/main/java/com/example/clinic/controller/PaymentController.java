package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.service.PatientService;
import com.example.clinic.service.PaymentApplicationService;
import com.example.clinic.service.PaymentApplicationService.PaymentResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.util.Map;

@Controller
public class PaymentController {
    private final PaymentApplicationService payments;
    private final PatientService patients;

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    public PaymentController(PaymentApplicationService payments, PatientService patients) {
        this.payments = payments;
        this.patients = patients;
    }

    @PostMapping("/create-checkout-session")
    public String createCheckoutSession(@RequestParam("appointmentId") Long appointmentId,
                                        Principal principal, HttpServletRequest request, RedirectAttributes ra) {
        var result = payments.createCheckoutSession(appointmentId, principal.getName(), baseUrl(request));
        return switch (result.status()) {
            case READY -> "redirect:" + result.url();
            case PAID -> "redirect:/payment-success?appointmentId=" + appointmentId;
            case DEMO_PAID -> {
                ra.addFlashAttribute("info", "Plată finalizată în modul demo local. Adăugați chei Stripe reale pentru plăți live.");
                yield "redirect:/payment-success?appointmentId=" + appointmentId;
            }
            case CLOSED -> {
                ra.addFlashAttribute("error", "Această programare este închisă și nu mai poate fi plătită.");
                yield "redirect:/patient/dashboard";
            }
            default -> {
                String message = switch (result.status()) {
                    case UNAVAILABLE -> "Plata online este temporar indisponibilă.";
                    case CONFIG_ERROR -> "Eroare de configurare plată. Contactați suportul.";
                    default -> "Nu s-a putut iniția plata. Vă rugăm să încercați din nou.";
                };
                ra.addFlashAttribute("error", message);
                yield "redirect:/patient/appointments/" + appointmentId + "/pay";
            }
        };
    }

    @PostMapping(value = "/create-payment-intent", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createPaymentIntent(@RequestParam("appointmentId") Long appointmentId,
                                                                  Principal principal) {
        return response(payments.createPaymentIntent(appointmentId, principal.getName()));
    }

    @PostMapping(value = "/apply-payment-coupon", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> applyPaymentCoupon(@RequestParam("appointmentId") Long appointmentId,
                                                                 @RequestParam("couponCode") String couponCode,
                                                                 Principal principal) {
        var result = payments.applyPaymentCoupon(appointmentId, couponCode, principal.getName());
        if (result.status() == PaymentApplicationService.PaymentStatus.SUCCESS) {
            var payload = new java.util.LinkedHashMap<>(result.data());
            payload.put("redirectUrl", "/payment-success?appointmentId=" + appointmentId);
            return ResponseEntity.ok(payload);
        }
        return response(result);
    }

    @GetMapping("/payment-success")
    public String paymentSuccess(@RequestParam("appointmentId") Long appointmentId,
                                 @RequestParam(name = "session_id", required = false) String sessionId,
                                 @RequestParam(name = "payment_intent", required = false) String paymentIntentId,
                                 Principal principal, Model model) {
        Appointment appointment;
        try {
            appointment = payments.verifyPaymentReturn(appointmentId, sessionId, paymentIntentId, principal.getName());
        } catch (PaymentApplicationService.PaymentMismatchException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        if (!appointment.isBookingFeePaid()) return "redirect:/payment-cancel?appointmentId=" + appointmentId;
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patients.getPatientByEmail(principal.getName()));
        return "PAID_REVIEW_REQUIRED".equals(appointment.getStripePaymentStatus())
                ? "patient/payment-review" : "patient/payment-success";
    }

    @GetMapping("/payment-cancel")
    public String paymentCancel(@RequestParam("appointmentId") Long appointmentId, Principal principal, Model model) {
        model.addAttribute("appointment", payments.getOwnedAppointment(appointmentId, principal.getName()));
        model.addAttribute("patient", patients.getPatientByEmail(principal.getName()));
        return "patient/payment-failed";
    }

    private ResponseEntity<Map<String, Object>> response(PaymentResult result) {
        HttpStatus status = switch (result.status()) {
            case SUCCESS -> HttpStatus.OK;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID -> HttpStatus.BAD_REQUEST;
            case PROVIDER_FAILURE -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(result.data());
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
