package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.RatingRepository;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceImplTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock UserRepository userRepository;
    @Mock RatingRepository ratingRepository;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;
    @Mock ActivityLogService activityLogService;
    @Mock WebSocketNotificationService wsNotifications;
    @Mock MetricsService metricsService;

    @InjectMocks DoctorServiceImpl service;

    private Appointment pendingPaidAppt;
    private Appointment confirmedAppt;
    private Appointment cancelledAppt;
    private Appointment completedAppt;

    @BeforeEach
    void setUp() {
        User doctorUser = new User();
        doctorUser.setEmail("dr.smith@clinic.com");
        doctorUser.setRole("DOCTOR");

        Doctor doctor = new Doctor();
        doctor.setFirstName("John");
        doctor.setLastName("Smith");
        doctor.setUser(doctorUser);

        User patientUser = new User();
        patientUser.setEmail("patient@example.com");
        patientUser.setRole("PATIENT");

        Patient patient = new Patient();
        patient.setFirstName("Maria");
        patient.setLastName("Popescu");
        patient.setUser(patientUser);

        pendingPaidAppt = new Appointment();
        pendingPaidAppt.setDoctor(doctor);
        pendingPaidAppt.setPatient(patient);
        pendingPaidAppt.setStatus("PENDING");
        pendingPaidAppt.setBookingFeePaid(true);
        pendingPaidAppt.setAppointmentDate(LocalDateTime.now().plusDays(1));

        confirmedAppt = new Appointment();
        confirmedAppt.setDoctor(doctor);
        confirmedAppt.setPatient(patient);
        confirmedAppt.setStatus("CONFIRMED");
        confirmedAppt.setBookingFeePaid(true);
        confirmedAppt.setAppointmentDate(LocalDateTime.now().plusDays(1));

        cancelledAppt = new Appointment();
        cancelledAppt.setDoctor(doctor);
        cancelledAppt.setPatient(patient);
        cancelledAppt.setStatus("CANCELLED");
        cancelledAppt.setAppointmentDate(LocalDateTime.now().plusDays(1));

        completedAppt = new Appointment();
        completedAppt.setDoctor(doctor);
        completedAppt.setPatient(patient);
        completedAppt.setStatus("COMPLETED");
        completedAppt.setAppointmentDate(LocalDateTime.now().minusDays(1));
    }

    @Test
    void confirmAppointment_happyPath_setsStatusAndNotifies() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingPaidAppt));
        when(appointmentRepository.save(any())).thenReturn(pendingPaidAppt);

        service.confirmAppointment(1L);

        assertThat(pendingPaidAppt.getStatus()).isEqualTo("CONFIRMED");
        verify(notificationService).notifyAppointmentConfirmed(pendingPaidAppt);
        verify(activityLogService).saveLog(eq(1L), eq("DOCTOR_CONFIRMED"), anyString());
        verify(auditLogService).log(anyString(), eq("APPOINTMENT_CONFIRMED"), anyString());
    }

    @Test
    void confirmAppointment_alreadyConfirmed_isIdempotent() {
        when(appointmentRepository.findById(2L)).thenReturn(Optional.of(confirmedAppt));

        service.confirmAppointment(2L);

        verify(appointmentRepository, never()).save(any());
        verify(notificationService, never()).notifyAppointmentConfirmed(any());
    }

    @Test
    void confirmAppointment_notFound_throwsAppointmentNotFoundException() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmAppointment(99L))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void confirmAppointment_notPendingStatus_throwsIllegalState() {
        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(cancelledAppt));

        assertThatThrownBy(() -> service.confirmAppointment(3L))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void confirmAppointment_feeNotPaid_throwsIllegalState() {
        pendingPaidAppt.setBookingFeePaid(false);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingPaidAppt));

        assertThatThrownBy(() -> service.confirmAppointment(1L))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("taxa de rezervare");
    }

    @Test
    void cancelAppointment_happyPath_setsStatusAndNotifies() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingPaidAppt));
        when(appointmentRepository.save(any())).thenReturn(pendingPaidAppt);

        service.cancelAppointment(1L);

        assertThat(pendingPaidAppt.getStatus()).isEqualTo("CANCELLED");
        verify(notificationService).notifyAppointmentCancelled(eq(pendingPaidAppt), anyString());
        verify(activityLogService).saveLog(eq(1L), eq("APPOINTMENT_CANCELLED"), anyString());
    }

    @Test
    void cancelAppointment_alreadyCancelled_isIdempotent() {
        when(appointmentRepository.findById(3L)).thenReturn(Optional.of(cancelledAppt));

        service.cancelAppointment(3L);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancelAppointment_completed_throwsIllegalState() {
        when(appointmentRepository.findById(4L)).thenReturn(Optional.of(completedAppt));

        assertThatThrownBy(() -> service.cancelAppointment(4L))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("finalizată");
    }

    @Test
    void completeAppointment_happyPath_setsStatusAndNotes() {
        when(appointmentRepository.findById(2L)).thenReturn(Optional.of(confirmedAppt));
        when(appointmentRepository.save(any())).thenReturn(confirmedAppt);

        service.completeAppointment(2L, "Follow-up in 2 weeks");

        assertThat(confirmedAppt.getStatus()).isEqualTo("COMPLETED");
        assertThat(confirmedAppt.getNotes()).isEqualTo("Follow-up in 2 weeks");
        verify(notificationService).notifyAppointmentCompleted(confirmedAppt);
        verify(activityLogService).saveLog(eq(2L), eq("APPOINTMENT_COMPLETED"), anyString());
    }

    @Test
    void completeAppointment_alreadyCompleted_isIdempotent() {
        when(appointmentRepository.findById(4L)).thenReturn(Optional.of(completedAppt));

        service.completeAppointment(4L, null);

        verify(appointmentRepository, never()).save(any());
        verify(notificationService, never()).notifyAppointmentCompleted(any());
    }

    @Test
    void completeAppointment_notConfirmed_throwsIllegalState() {
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(pendingPaidAppt));

        assertThatThrownBy(() -> service.completeAppointment(1L, null))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("confirmate");
    }

    @Test
    void completeAppointment_nullNotes_doesNotOverwriteExistingNotes() {
        confirmedAppt.setNotes("Original notes");
        when(appointmentRepository.findById(2L)).thenReturn(Optional.of(confirmedAppt));
        when(appointmentRepository.save(any())).thenReturn(confirmedAppt);

        service.completeAppointment(2L, null);

        assertThat(confirmedAppt.getNotes()).isEqualTo("Original notes");
    }
}
