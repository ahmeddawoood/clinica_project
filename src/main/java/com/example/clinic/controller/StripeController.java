package com.example.clinic.controller;

import com.example.clinic.service.StripeWebhookService;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Tag(name = "Plăți Stripe",
     description = "Webhook Stripe pentru procesarea evenimentelor de plată și rambursare")
public class StripeController {

    private static final Logger log = LoggerFactory.getLogger(StripeController.class);

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    private final StripeWebhookService stripeWebhookService;

    public StripeController(StripeWebhookService stripeWebhookService) {
        this.stripeWebhookService = stripeWebhookService;
    }

    @PostMapping("/stripe/webhook")
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

        try {
            stripeWebhookService.processVerifiedEvent(event);
        } catch (com.example.clinic.service.StripePaymentVerification.RejectedPaymentException ex) {
            log.warn("Stripe payment rejected: type={} eventId={}", event.getType(), event.getId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Payment verification failed");
        }

        return ResponseEntity.ok("OK");
    }

}
