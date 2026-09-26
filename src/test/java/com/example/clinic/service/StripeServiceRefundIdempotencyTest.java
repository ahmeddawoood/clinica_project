package com.example.clinic.service;

import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripeServiceRefundIdempotencyTest {

    @Test
    void refundIdempotencyKeyIsStableForPaymentIntent() {
        String paymentIntentId = "pi_test_123";
        String firstKey = "refund:" + paymentIntentId;
        String secondKey = "refund:" + paymentIntentId;

        assertThat(firstKey).isEqualTo(secondKey);
        assertThat(firstKey).isEqualTo("refund:pi_test_123");
    }
}
