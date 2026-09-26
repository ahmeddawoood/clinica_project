package com.example.clinic.ai;

import com.example.clinic.domain.Doctor;
import com.example.clinic.service.IntelligentAssignmentService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvailableDoctorsToolTest {

    private final IntelligentAssignmentService assignmentService = mock(IntelligentAssignmentService.class);
    private final AvailableDoctorsTool tool = new AvailableDoctorsTool(assignmentService);

    @Test
    void returnsAvailableDoctorsFromExistingAssignmentService() {
        Doctor doctor = new Doctor();
        doctor.setFirstName("Sara");
        doctor.setLastName("Ionescu");
        doctor.setSpecialty("Cardiology");

        when(assignmentService.rankDoctors("Cardiology", LocalDateTime.of(2026, 10, 5, 10, 0)))
                .thenReturn(List.of(new IntelligentAssignmentService.DoctorRecommendation(
                        doctor, 100, 0, 0)));

        var result = tool.findAvailableDoctors(" Cardiology ", "2026-10-05T10:00:00");

        assertEquals(1, result.size());
        assertEquals("Sara Ionescu", result.get(0).fullName());
        assertEquals("Cardiology", result.get(0).specialty());
        verify(assignmentService).rankDoctors("Cardiology", LocalDateTime.of(2026, 10, 5, 10, 0));
    }

    @Test
    void rejectsMissingDateTime() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.findAvailableDoctors("Cardiology", ""));
        verifyNoInteractions(assignmentService);
    }

    @Test
    void rejectsInvalidDateTime() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.findAvailableDoctors("Cardiology", "tomorrow morning"));
        verifyNoInteractions(assignmentService);
    }
}
