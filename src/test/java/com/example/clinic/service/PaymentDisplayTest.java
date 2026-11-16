package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentDisplayTest {
    @Test void configuredBookingFeeUsesItsCurrencyAndMinorUnits() {
        assertThat(new PaymentDisplay(750, "ron").getBookingFee()).isEqualTo("7.50 RON");
        assertThat(new PaymentDisplay(200, "usd").getBookingFee()).isEqualTo("2.00 USD");
    }
    @Test void historicalPaymentKeepsRecordedCurrencyAfterConfigurationChanges() {
        Appointment appointment = new Appointment();
        appointment.setStripeAmountCents(200L);
        appointment.setStripeCurrency("usd");
        assertThat(new PaymentDisplay(750, "ron").paid(appointment)).isEqualTo("2.00 USD");
    }
    @Test void waivedPaymentDoesNotDisplayTheBookingFee() {
        Appointment appointment = new Appointment();
        appointment.setStripeAmountCents(0L);
        appointment.setStripeCurrency("ron");
        assertThat(new PaymentDisplay(750, "ron").paid(appointment)).isEqualTo("0.00 RON");
    }
    @Test void incompletePaymentDataDoesNotInventAnAmountOrCurrency() {
        assertThat(PaymentDisplay.format(null, "usd")).isEqualTo("Sumă indisponibilă");
        assertThat(PaymentDisplay.format(200L, null)).isEqualTo("Sumă indisponibilă");
        assertThat(PaymentDisplay.format(200L, "invalid")).isEqualTo("Sumă indisponibilă");
    }
}
