package com.example.clinic.security;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.AiService;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.ActivityLogService;
import com.example.clinic.service.MetricsService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import com.example.clinic.service.WebSocketNotificationService;
import com.example.clinic.service.PatientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppointmentBookingConcurrencyTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;
    @MockitoBean WebSocketNotificationService webSocketNotificationService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean ActivityLogService activityLogService;
    @MockitoBean MetricsService metricsService;
    @MockitoBean AiService aiService;

    @Autowired PatientService patientService;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired AppointmentRepository appointmentRepository;

    @Test
    void concurrentBookingsForSameDoctorAndSlot_allowOnlyOne() throws Exception {
        when(aiService.analyzeSymptoms(anyString())).thenReturn(
                new com.example.clinic.dto.SymptomAnalysisResult(
                        "LOW", false, "General", "Routine", 60));

        User doctorUser = user("concurrency-doctor@test.com", "DOCTOR");
        Doctor doctor = new Doctor();
        doctor.setFirstName("Concurrency");
        doctor.setLastName("Doctor");
        doctor.setSpecialty("General");
        doctor.setUser(doctorUser);
        doctor = doctorRepository.save(doctor);
        final Doctor finalDoctor = doctor;

        List<String> patientEmails = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            User user = user("concurrency-patient-" + i + "@test.com", "PATIENT");
            Patient patient = new Patient();
            patient.setFirstName("Concurrency");
            patient.setLastName("Patient" + i);
            patient.setUser(user);
            patientRepository.save(patient);
            patientEmails.add(user.getEmail());
        }

        LocalDateTime slot = LocalDateTime.of(2097, 2, 20, 10, 0);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Long>> calls = patientEmails.stream()
                    .map(email -> (Callable<Long>) () -> {
                        AppointmentForm form = new AppointmentForm();
                        form.setDoctorId(finalDoctor.getId());
                        form.setAppointmentDate(slot);
                        form.setNotes("concurrency test");
                        return patientService.bookAppointment(form, email);
                    })
                    .toList();

            List<Future<Long>> futures = pool.invokeAll(calls);
            long successes = 0;
            for (Future<Long> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (Exception ignored) {
                    // A conflicting booking is expected to fail.
                }
            }

            long bookings = appointmentRepository.countConflicts(doctor, slot);
            assertThat(successes).isEqualTo(1);
            assertThat(bookings).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }
}
