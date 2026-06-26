package com.example.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Detalii programare medicală")
public record AppointmentResponse(
        @Schema(description = "ID-ul programării", example = "42")
        Long id,

        @Schema(description = "Statusul curent", example = "CONFIRMED",
                allowableValues = {"PENDING_PAYMENT", "PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"})
        String status,

        @Schema(description = "Data și ora programării", example = "2025-06-15T10:00:00")
        LocalDateTime appointmentDate,

        @Schema(description = "Prioritatea AI", example = "LOW",
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        String priority,

        @Schema(description = "Notițe / simptome", example = "Durere de cap persistentă")
        String notes,

        @Schema(description = "Doctor alocat")
        DoctorResponse doctor,

        @Schema(description = "Motivul anulării (dacă există)", nullable = true)
        String cancelReason,

        @Schema(description = "Data creării")
        LocalDateTime createdAt
) {}
