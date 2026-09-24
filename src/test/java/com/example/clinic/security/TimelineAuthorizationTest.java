package com.example.clinic.security;

import com.example.clinic.domain.ActivityLog;
import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.repository.ActivityLogRepository;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimelineAuthorizationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired WebApplicationContext wac;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ActivityLogRepository activityLogRepository;

    private MockMvc mockMvc;
    private Appointment patientBAppointment;

    @BeforeAll
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User patientBUser = user("timeline-patient-b@test.com", "PATIENT");
        User doctorUser = user("timeline-doctor@test.com", "DOCTOR");

        Patient patientB = new Patient();
        patientB.setFirstName("Patient");
        patientB.setLastName("B");
        patientB.setUser(patientBUser);
        patientB = patientRepository.save(patientB);

        Doctor doctor = new Doctor();
        doctor.setFirstName("Doctor");
        doctor.setLastName("Test");
        doctor.setSpecialty("General");
        doctor.setUser(doctorUser);
        doctor = doctorRepository.save(doctor);

        patientBAppointment = new Appointment();
        patientBAppointment.setPatient(patientB);
        patientBAppointment.setDoctor(doctor);
        patientBAppointment.setAppointmentDate(LocalDateTime.of(2099, 1, 10, 10, 0));
        patientBAppointment.setStatus("CONFIRMED");
        patientBAppointment = appointmentRepository.save(patientBAppointment);

        activityLogRepository.save(
                new ActivityLog(
                        patientBAppointment.getId(),
                        "BOOKING_CREATED",
                        "test"
                )
        );
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }

    @Test
    @WithMockUser(username = "timeline-patient-a@test.com", roles = "PATIENT")
    void patientCannotReadAnotherPatientsAppointmentTimeline() throws Exception {
        mockMvc.perform(get("/appointments/{id}/timeline", patientBAppointment.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "timeline-patient-b@test.com", roles = "PATIENT")
    void patientCanReadOwnAppointmentTimeline() throws Exception {
        mockMvc.perform(get("/appointments/{id}/timeline", patientBAppointment.getId()))
                .andExpect(status().isOk());
    }
}
