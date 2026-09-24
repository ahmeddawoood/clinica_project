package com.example.clinic.security;

import com.example.clinic.domain.Message;
import com.example.clinic.domain.User;
import com.example.clinic.repository.MessageRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageAuthorizationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired WebApplicationContext wac;
    @Autowired UserRepository userRepository;
    @Autowired MessageRepository messageRepository;

    private MockMvc mockMvc;
    private Message patientAMessageToPatientB;
    private Message patientBMessageToDoctor;

    @BeforeAll
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User patientA = user("timeline-message-patient-a@test.com", "PATIENT");
        User patientB = user("timeline-message-patient-b@test.com", "PATIENT");
        User doctor = user("timeline-message-doctor@test.com", "DOCTOR");

        patientAMessageToPatientB = message(patientA, patientB);
        patientBMessageToDoctor = message(patientB, doctor);
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Message message(User sender, User receiver) {
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setSubject("Test subject");
        message.setBody("Test body");
        return messageRepository.save(message);
    }

    @Test
    @WithMockUser(username = "patient-a@test.com", roles = "PATIENT")
    void patientCannotReadMessageBetweenOtherUsers() throws Exception {
        mockMvc.perform(get("/patient/messages/{id}", patientBMessageToDoctor.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patient-b@test.com", roles = "PATIENT")
    void messageReceiverCanReadOwnMessage() throws Exception {
        mockMvc.perform(get("/patient/messages/{id}", patientAMessageToPatientB.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "timeline-message-patient-a@test.com", roles = "PATIENT")
    void patientCannotReadAnotherUsersDoctorMessage() throws Exception {
        mockMvc.perform(get("/doctor/messages/{id}", patientBMessageToDoctor.getId()))
                .andExpect(status().isForbidden());
    }
}
