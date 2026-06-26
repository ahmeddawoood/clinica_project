package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
public interface StripeService {
    Session createCheckoutSession(Appointment appointment, String successUrl, String cancelUrl)
            throws StripeException;
    Session createCheckoutSession(Appointment appointment, String successUrl, String cancelUrl,
                                  String idempotencyKey) throws StripeException;
    PaymentIntent createPaymentIntent(Appointment appointment, String idempotencyKey) throws StripeException;
    void refund(String paymentIntentId) throws StripeException;
    PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException;
    Session retrieveSession(String sessionId) throws StripeException;

    boolean isEnabled();
}
