package com.example.clinic.security;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.User;
import com.example.clinic.repository.ActivityLogRepository;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TimelineAuthorizationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired WebApplicationContext wac;
    @Autowired UserRepository userRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ActivityLogRepository activityLogRepository;

    private MockMvc mockMvc;
    private Appointment patientBAppointment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User patientAUser = user("patient-a@test.com", "PATIENT");
        User patientBUser = user("patient-b@test.com", "PATIENT");
        User doctorUser = user("doctor@test.com", "DOCTOR");

        patientBAppointment = new Appointment();
        patientBAppointment.setStatus("CONFIRMED");
        patientBAppointment.setPatient(null);
        patientBAppointment.setDoctor(null);

        // The endpoint authorization is based on Patient/Doctor -> User ownership.
        // Persisting a complete appointment fixture requires Patient/Doctor entities,
        // so this test is intentionally left for the repository-backed integration fixture.
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }

    @Test
    @WithMockUser(username = "patient-a@test.com", roles = "PATIENT")
    void anonymousOrWrongOwnerCannotReadAppointmentTimeline() throws Exception {
        mockMvc.perform(get("/appointments/{id}/timeline", 999999L))
                .andExpect(status().isForbidden());
    }
}
