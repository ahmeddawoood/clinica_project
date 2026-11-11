package com.example.clinic.config;

import com.example.clinic.domain.User;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String password = "a-test-only-secret-12345";

    private void run(String email, String secret, boolean demo) {
        new AdminBootstrap(users, encoder, email, secret, demo)
                .run(new DefaultApplicationArguments());
    }

    @Test
    void createsEnabledAdminWithNormalizedEmailAndHashedPassword() {
        run(" Owner@Example.test ", password, false);
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("owner@example.test");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo(password);
        assertThat(encoder.matches(password, admin.getPassword())).isTrue();
    }

    @Test
    void refusesExistingAdminWithoutResettingAnything() {
        when(users.existsByRole("ADMIN")).thenReturn(true);
        assertThatThrownBy(() -> run("owner@example.test", password, false))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refusesToPromoteExistingPatient() {
        when(users.findByEmail("owner@example.test")).thenReturn(Optional.of(new User()));
        assertThatThrownBy(() -> run("owner@example.test", password, false))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refusesDemoMode() {
        assertThatThrownBy(() -> run("owner@example.test", password, true))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "                 ", "🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐🔐"})
    void rejectsInvalidPasswordsWithoutExposingThem(String secret) {
        assertThatThrownBy(() -> run("owner@example.test", secret, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Admin bootstrap password must");
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "owner", "owner@", "owner name@example.test"})
    void rejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> run(email, password, false))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users);
    }
}
