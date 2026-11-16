package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/** Displays minor-unit amounts without converting or relabelling historical payments. */
@Component
public class PaymentDisplay {
    private final long bookingFeeCents;
    private final String currency;

    public PaymentDisplay(@Value("${stripe.booking-fee-cents:200}") long bookingFeeCents,
                          @Value("${stripe.currency:usd}") String currency) {
        this.bookingFeeCents = bookingFeeCents;
        this.currency = currency;
    }

    public String getBookingFee() {
        return format(bookingFeeCents, currency);
    }

    public String paid(Appointment appointment) {
        return format(appointment.getStripeAmountCents(), appointment.getStripeCurrency());
    }

    public static String format(Long minorUnits, String currencyCode) {
        if (minorUnits == null || currencyCode == null || currencyCode.isBlank()) {
            return "Sumă indisponibilă";
        }
        try {
            Currency currency = Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ROOT));
            int digits = currency.getDefaultFractionDigits();
            if (digits < 0) return "Sumă indisponibilă";
            return BigDecimal.valueOf(minorUnits, digits).setScale(digits).toPlainString()
                    + " " + currency.getCurrencyCode();
        } catch (IllegalArgumentException ex) {
            return "Sumă indisponibilă";
        }
    }
}
