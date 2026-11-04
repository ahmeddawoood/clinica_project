package com.example.clinic.ai;

import com.example.clinic.domain.Appointment;
import com.example.clinic.service.PatientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyAppointmentsToolTest {

    @Mock
    PatientService patientService;

    @Mock
    Appointment appointment;

    private MyAppointmentsTool tool() {
        return new MyAppointmentsTool(patientService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsUnauthenticatedAccess() {
        assertThrows(IllegalStateException.class, () -> tool().getMyAppointments());
    }

    @Test
    void usesAuthenticatedIdentityAndDoesNotAcceptAnArbitraryPatientId() {
        var authentication = new UsernamePasswordAuthenticationToken("patient@example.com", "N/A", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(patientService.getAppointments("patient@example.com")).thenReturn(List.of(appointment));

        var result = tool().getMyAppointments();

        assertEquals(1, result.size());
        verify(patientService).getAppointments("patient@example.com");
    }
}
