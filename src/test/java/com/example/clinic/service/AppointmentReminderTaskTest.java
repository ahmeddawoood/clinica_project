package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderTaskTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock NotificationService notificationService;

    @InjectMocks AppointmentReminderTask task;

    private Appointment makeConfirmedAppointment() {
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
        patient.setFirstName("Ana");
        patient.setLastName("Ionescu");
        patient.setUser(patientUser);

        Appointment appt = new Appointment();
        appt.setStatus("CONFIRMED");
        appt.setDoctor(doctor);
        appt.setPatient(patient);
        appt.setAppointmentDate(LocalDateTime.now().plusDays(1).withHour(10));
        return appt;
    }

    @Test
    void sendDailyReminders_confirmedAppointmentsTomorrow_reminderSent() {
        Appointment appt = makeConfirmedAppointment();
        when(appointmentRepository.findByStatusAndAppointmentDateBetween(
                eq("CONFIRMED"), any(), any()))
                .thenReturn(List.of(appt));

        task.sendDailyReminders();

        verify(notificationService).notifyAppointmentReminder(appt);
    }

    @Test
    void sendDailyReminders_noUpcomingAppointments_noNotificationsSent() {
        when(appointmentRepository.findByStatusAndAppointmentDateBetween(
                anyString(), any(), any()))
                .thenReturn(List.of());

        task.sendDailyReminders();

        verifyNoInteractions(notificationService);
    }

    @Test
    void sendDailyReminders_multipleAppointments_reminderSentForEach() {
        Appointment appt1 = makeConfirmedAppointment();
        Appointment appt2 = makeConfirmedAppointment();
        when(appointmentRepository.findByStatusAndAppointmentDateBetween(
                eq("CONFIRMED"), any(), any()))
                .thenReturn(List.of(appt1, appt2));

        task.sendDailyReminders();

        verify(notificationService, times(2)).notifyAppointmentReminder(any());
    }

    @Test
    void sendDailyReminders_queriesForConfirmedStatusOnly() {
        when(appointmentRepository.findByStatusAndAppointmentDateBetween(
                anyString(), any(), any()))
                .thenReturn(List.of());

        task.sendDailyReminders();

        verify(appointmentRepository).findByStatusAndAppointmentDateBetween(
                eq("CONFIRMED"), any(), any());
    }
}
