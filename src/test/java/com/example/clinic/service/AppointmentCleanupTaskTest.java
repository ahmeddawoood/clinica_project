package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentCleanupTaskTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;
    @Mock ActivityLogService activityLogService;

    @InjectMocks AppointmentCleanupTask task;

    private Appointment makeExpiredAppointment(long id) {
        User doctorUser = new User();
        doctorUser.setEmail("dr@clinic.com");
        doctorUser.setRole("DOCTOR");
        doctorUser.setPassword("x");

        Doctor doctor = new Doctor();
        doctor.setFirstName("John");
        doctor.setLastName("Smith");
        doctor.setUser(doctorUser);

        User patientUser = new User();
        patientUser.setEmail("patient@example.com");
        patientUser.setRole("PATIENT");
        patientUser.setPassword("x");

        Patient patient = new Patient();
        patient.setFirstName("Maria");
        patient.setLastName("Popescu");
        patient.setUser(patientUser);

        Appointment appt = new Appointment();
        ReflectionTestUtils.setField(appt, "id", id);
        appt.setStatus("PENDING_PAYMENT");
        appt.setDoctor(doctor);
        appt.setPatient(patient);
        appt.setAppointmentDate(LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(appt, "createdAt", LocalDateTime.now().minusHours(2));
        return appt;
    }

    @Test
    void cancelExpiredUnpaidAppointments_setsStatusToCancelled() {
        Appointment appt = makeExpiredAppointment(1L);
        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq("PENDING_PAYMENT"), any()))
                .thenReturn(List.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        task.cancelExpiredUnpaidAppointments();

        assertThat(appt.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelExpiredUnpaidAppointments_setsCancelReasonWithAutoMessage() {
        Appointment appt = makeExpiredAppointment(1L);
        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq("PENDING_PAYMENT"), any()))
                .thenReturn(List.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        task.cancelExpiredUnpaidAppointments();

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getCancelReason())
                .contains("automat")
                .contains("taxa de rezervare");
    }

    @Test
    void cancelExpiredUnpaidAppointments_emptyList_doesNothing() {
        when(appointmentRepository.findByStatusAndCreatedAtBefore(anyString(), any()))
                .thenReturn(List.of());

        task.cancelExpiredUnpaidAppointments();

        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(auditLogService, notificationService, activityLogService);
    }

    @Test
    void cancelExpiredUnpaidAppointments_multipleAppointments_eachProcessed() {
        Appointment appt1 = makeExpiredAppointment(1L);
        Appointment appt2 = makeExpiredAppointment(2L);
        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq("PENDING_PAYMENT"), any()))
                .thenReturn(List.of(appt1, appt2));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.cancelExpiredUnpaidAppointments();

        verify(appointmentRepository, times(2)).save(any());
        verify(notificationService, times(2)).notifyAppointmentCancelled(any(), anyString());
        verify(auditLogService, times(2)).log(eq("system"), eq("AUTO_CANCEL"), anyString());
        verify(activityLogService, times(2)).saveLog(any(), eq("APPOINTMENT_CANCELLED"), anyString());
    }

    @Test
    void cancelExpiredUnpaidAppointments_auditLoggedWithSystemUser() {
        Appointment appt = makeExpiredAppointment(10L);
        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq("PENDING_PAYMENT"), any()))
                .thenReturn(List.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        task.cancelExpiredUnpaidAppointments();

        verify(auditLogService).log(eq("system"), eq("AUTO_CANCEL"), anyString());
    }

    @Test
    void cancelExpiredUnpaidAppointments_notificationSentWithCancelReason() {
        Appointment appt = makeExpiredAppointment(5L);
        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq("PENDING_PAYMENT"), any()))
                .thenReturn(List.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        task.cancelExpiredUnpaidAppointments();

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyAppointmentCancelled(eq(appt), reasonCaptor.capture());
        assertThat(reasonCaptor.getValue()).contains("taxa de rezervare");
    }
}
