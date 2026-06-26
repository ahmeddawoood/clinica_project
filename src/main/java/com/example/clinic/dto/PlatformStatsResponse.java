package com.example.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Statistici publice ale platformei MediClinic")
public record PlatformStatsResponse(
        @Schema(description = "Număr total doctori înregistrați", example = "12")
        long totalDoctors,

        @Schema(description = "Număr total pacienți înregistrați", example = "248")
        long totalPatients,

        @Schema(description = "Număr total programări procesate", example = "1032")
        long totalAppointments,

        @Schema(description = "Lista specialităților medicale disponibile")
        List<String> specialties
) {}
