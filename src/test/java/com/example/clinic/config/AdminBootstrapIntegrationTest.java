package com.example.clinic.config;

import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.bootstrap-admin.enabled=true",
        "app.bootstrap-admin.email=first-admin@example.test",
        "app.bootstrap-admin.password=fixture-admin-secret-123",
        "app.demo-data.enabled=false"
})
@ActiveProfiles("test")
class AdminBootstrapIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void provisionsOnlyOneHashedAdministratorDuringStartup() {
        assertThat(users.count()).isEqualTo(1);
        var admin = users.findByEmail("first-admin@example.test").orElseThrow();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(encoder.matches("fixture-admin-secret-123", admin.getPassword())).isTrue();
    }
}
