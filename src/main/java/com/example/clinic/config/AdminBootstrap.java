package com.example.clinic.config;

import com.example.clinic.domain.User;
import com.example.clinic.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Explicit first-install operation. Never promotes or resets an existing user. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    private final boolean demoEnabled;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password,
            @Value("${app.demo-data.enabled:false}") boolean demoEnabled) {
        this.users = users;
        this.encoder = encoder;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
        this.demoEnabled = demoEnabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoEnabled) {
            throw new IllegalStateException("Admin bootstrap requires demo data to be disabled");
        }
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalStateException("Admin bootstrap requires a valid email");
        }
        if (password.isBlank() || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Admin bootstrap password must have at least 16 characters and at most 72 UTF-8 bytes");
        }
        if (users.existsByRole("ADMIN") || users.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Admin bootstrap refused: administrator or requested email already exists; disable bootstrap");
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(encoder.encode(password));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        users.saveAndFlush(admin);
    }
}
