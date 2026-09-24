package com.example.clinic.security;

import com.example.clinic.domain.User;
import com.example.clinic.dto.RegisterRequest;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.UserService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationAuthorizationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;

    @Test
    void publicRegistrationCannotCreateAdminAccount() {
        String email = "security-role-escalation-admin@test.com";

        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Attacker");
        request.setLastName("Test");
        request.setEmail(email);
        request.setPassword("Password123");
        request.setRole("ADMIN");

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rolul nu poate fi creat prin inregistrarea publica.");

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void publicRegistrationStillAllowsPatientAccount() {
        String email = "security-role-escalation-patient@test.com";

        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Patient");
        request.setLastName("Test");
        request.setEmail(email);
        request.setPassword("Password123");
        request.setRole("PATIENT");

        userService.register(request);

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo("PATIENT");
    }
}
