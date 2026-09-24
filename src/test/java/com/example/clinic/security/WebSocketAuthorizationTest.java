package com.example.clinic.security;

import com.example.clinic.config.WebSocketAuthorizationInterceptor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSocketAuthorizationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired WebSocketAuthorizationInterceptor interceptor;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;

    private Long patientAId;
    private Long patientBId;

    @BeforeAll
    void setUp() {
        User patientAUser = user("security-ws-patient-a@test.com", "PATIENT");
        User patientBUser = user("security-ws-patient-b@test.com", "PATIENT");

        patientAId = patient(patientAUser, "A").getId();
        patientBId = patient(patientBUser, "B").getId();
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Patient patient(User user, String lastName) {
        Patient patient = new Patient();
        patient.setFirstName("WS");
        patient.setLastName(lastName);
        patient.setUser(user);
        return patientRepository.save(patient);
    }

    @Test
    void anonymousWebSocketConnectIsRejected() {
        Message<?> message = stomp(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void patientCannotSubscribeToAnotherPatientsNotifications() {
        Message<?> message = stomp(
                StompCommand.SUBSCRIBE,
                "/topic/patient/" + patientBId + "/notifications",
                principal("security-ws-patient-a@test.com")
        );

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void patientCanSubscribeToOwnNotifications() {
        Message<?> message = stomp(
                StompCommand.SUBSCRIBE,
                "/topic/patient/" + patientAId + "/notifications",
                principal("security-ws-patient-a@test.com")
        );

        assertThatCode(() -> interceptor.preSend(message, null))
                .doesNotThrowAnyException();
    }

    private Message<?> stomp(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(principal);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
