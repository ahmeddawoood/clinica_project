package com.example.clinic.ai;

import com.example.clinic.domain.Doctor;
import com.example.clinic.service.IntelligentAssignmentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AvailableDoctorsTool {

    private final IntelligentAssignmentService assignmentService;

    public AvailableDoctorsTool(IntelligentAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Tool(description = "Find doctors who are available at a requested date and time. Optionally filter by specialty. Use this only for availability discovery. Do not book, modify, or cancel appointments.")
    public List<AvailableDoctor> findAvailableDoctors(String specialty, String requestedDateTime) {
        LocalDateTime requestedAt = parseDateTime(requestedDateTime);

        return assignmentService.rankDoctors(normalizeSpecialty(specialty), requestedAt).stream()
                .map(recommendation -> AvailableDoctor.from(recommendation.getDoctor(), recommendation.getAvailabilityLabel()))
                .toList();
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("requestedDateTime is required");
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("requestedDateTime must use ISO-8601 local date-time format", ex);
        }
    }

    private static String normalizeSpecialty(String specialty) {
        return specialty == null || specialty.isBlank() ? null : specialty.trim();
    }

    public record AvailableDoctor(Long id, String fullName, String specialty, String availability) {
        static AvailableDoctor from(Doctor doctor, String availability) {
            return new AvailableDoctor(doctor.getId(), doctor.getFullName(), doctor.getSpecialty(), availability);
        }
    }
}
