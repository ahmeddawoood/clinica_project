package com.example.clinic.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiToolRegistryTest {

    private final MyAppointmentsTool appointmentsTool = mock(MyAppointmentsTool.class);
    private final AiToolRegistry registry = new AiToolRegistry(appointmentsTool);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesToolsOnlyToAuthenticatedPatients() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "patient@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));

        assertEquals(List.of(appointmentsTool), registry.toolsForCurrentUser());
    }

    @Test
    void rejectsAuthenticatedNonPatientUsers() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "doctor@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));

        assertThrows(IllegalStateException.class, registry::toolsForCurrentUser);
    }

    @Test
    void rejectsUnauthenticatedUsers() {
        SecurityContextHolder.clearContext();

        assertThrows(IllegalStateException.class, registry::toolsForCurrentUser);
    }
}
