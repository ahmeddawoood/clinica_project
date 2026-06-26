package com.example.clinic.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final Counter bookingsCreated;
    private final Counter bookingsCancelled;
    private final Counter refunds;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.bookingsCreated   = registry.counter("clinic.bookings.created");
        this.bookingsCancelled = registry.counter("clinic.bookings.cancelled");
        this.refunds           = registry.counter("clinic.refunds.issued");
    }

    public void bookingCreated() {
        bookingsCreated.increment();
    }

    public void bookingCancelled() {
        bookingsCancelled.increment();
    }

    public void bookingCompleted() {
        registry.counter("clinic.bookings.completed").increment();
    }

    public void refundIssued() {
        refunds.increment();
    }
    public void emailSent() {
        registry.counter("clinic.notifications.emails").increment();
    }

    public void smsSent() {
        registry.counter("clinic.notifications.sms").increment();
    }
}
