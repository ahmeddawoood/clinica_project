package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClosedAppointmentPaymentTest {
    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED"})
    void refusesAllPaymentEntryPointsBeforeProvidersOrWrites(String status) {
        var appointments = mock(AppointmentRepository.class);
        var invoices = mock(InvoiceRepository.class);
        var patients = mock(PatientService.class);
        var stripe = mock(StripeService.class);
        var notifications = mock(NotificationService.class);
        var audit = mock(AuditLogService.class);
        var activity = mock(ActivityLogService.class);
        var payments = new PaymentApplicationService(appointments, invoices, stripe, new StripePaymentVerification(200L, "usd"), notifications, audit, activity);
        var controller = new PaymentController(payments, patients);
        ReflectionTestUtils.setField(payments, "demoFallbackEnabled", true);
        ReflectionTestUtils.setField(payments, "paymentCouponCode", "test-coupon");
        var user = new User();
        user.setEmail("owner@example.test");
        var patient = new Patient();
        patient.setUser(user);
        var appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setStatus(status);
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));

        assertThat(controller.applyPaymentCoupon(1L, "test-coupon", user::getEmail).getStatusCode().value()).isEqualTo(409);
        assertThat(controller.createPaymentIntent(1L, user::getEmail).getStatusCode().value()).isEqualTo(409);
        assertThat(controller.createCheckoutSession(1L, user::getEmail, new MockHttpServletRequest(),
                new RedirectAttributesModelMap())).isEqualTo("redirect:/patient/dashboard");
        assertThat(appointment.getStatus()).isEqualTo(status);
        assertThat(appointment.isBookingFeePaid()).isFalse();
        verify(appointments, never()).save(any());
        verifyNoInteractions(invoices, patients, stripe, notifications, audit, activity);
    }
    @org.junit.jupiter.api.Test
    void successfulPaymentForAnotherAppointmentCannotBeReused() throws Exception {
        var appointments=mock(AppointmentRepository.class);
        var invoices=mock(InvoiceRepository.class);
        var stripe=mock(StripeService.class);
        var notifications=mock(NotificationService.class);
        var payments=new PaymentApplicationService(appointments,invoices,stripe,new StripePaymentVerification(200L,"usd"),
                notifications,mock(AuditLogService.class),mock(ActivityLogService.class));
        var controller=new PaymentController(payments,mock(PatientService.class));
        var user=new User();user.setEmail("owner@example.test");
        var patient=new Patient();patient.setUser(user);
        var appointment=new Appointment();appointment.setPatient(patient);appointment.setStatus("PENDING_PAYMENT");
        ReflectionTestUtils.setField(appointment,"id",1L);
        when(appointments.findByIdForUpdate(1L)).thenReturn(Optional.of(appointment));
        var intent=new com.stripe.model.PaymentIntent();intent.setId("pi_other");intent.setStatus("succeeded");
        intent.setMetadata(java.util.Map.of("appointmentId","999"));
        when(stripe.retrievePaymentIntent("pi_other")).thenReturn(intent);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.paymentSuccess(1L,null,"pi_other",
                user::getEmail,new org.springframework.ui.ExtendedModelMap()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(appointment.isBookingFeePaid()).isFalse();
        verify(appointments,never()).save(any());verifyNoInteractions(invoices,notifications);
    }
}
