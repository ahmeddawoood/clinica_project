package com.example.clinic.security;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.dto.SymptomAnalysisResult;
import com.example.clinic.exception.DuplicateBookingException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.ActivityLogService;
import com.example.clinic.service.AiService;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.MetricsService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.PatientService;
import com.example.clinic.service.StripeService;
import com.example.clinic.service.WebSocketNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentBookingConcurrencyTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;
    @MockitoBean ActivityLogService activityLogService;
    @MockitoBean AuditLogService auditLogService;
    @MockitoBean WebSocketNotificationService wsNotifications;
    @MockitoBean MetricsService metricsService;
    @MockitoBean AiService aiService;

    @Autowired PatientService patientService;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;

    @Test
    void concurrentBookingsForSameDoctorAndSlotAllowOnlyOneAppointment() throws Exception {
        when(aiService.analyzeSymptoms(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new SymptomAnalysisResult("LOW", false, "General", "none"));

        Doctor doctor = doctor("security-race-doctor@test.com");
        Patient patientA = patient("security-race-patient-a@test.com");
        Patient patientB = patient("security-race-patient-b@test.com");

        AppointmentForm formA = form(doctor.getId());
        AppointmentForm formB = form(doctor.getId());

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Object> first = executor.submit(() -> bookAfterBarrier(barrier, formA, patientA));
            Future<Object> second = executor.submit(() -> bookAfterBarrier(barrier, formB, patientB));

            List<Object> results = List.of(first.get(), second.get());

            assertThat(results.stream().filter(Long.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(DuplicateBookingException.class::isInstance).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Object bookAfterBarrier(CyclicBarrier barrier, AppointmentForm form, Patient patient) {
        try {
            barrier.await();
            return patientService.bookAppointment(form, patient.getUser().getEmail());
        } catch (Exception ex) {
            return ex;
        }
    }

    private AppointmentForm form(Long doctorId) {
        AppointmentForm form = new AppointmentForm();
        form.setDoctorId(doctorId);
        form.setAppointmentDate(LocalDateTime.of(2099, 2, 10, 10, 0));
        form.setNotes("concurrency test");
        return form;
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Doctor doctor(String email) {
        User user = user(email, "DOCTOR");
        Doctor doctor = new Doctor();
        doctor.setFirstName("Race");
        doctor.setLastName("Doctor");
        doctor.setSpecialty("General");
        doctor.setUser(user);
        return doctorRepository.save(doctor);
    }

    private Patient patient(String email) {
        User user = user(email, "PATIENT");
        Patient patient = new Patient();
        patient.setFirstName("Race");
        patient.setLastName("Patient");
        patient.setUser(user);
        return patientRepository.save(patient);
    }
}
