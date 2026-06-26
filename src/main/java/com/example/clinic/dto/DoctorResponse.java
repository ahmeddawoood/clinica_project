package com.example.clinic.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Informații despre un doctor din clinică")
public record DoctorResponse(
        @Schema(description = "ID-ul unic al doctorului", example = "1")
        Long id,

        @Schema(description = "Prenumele doctorului", example = "Mihai")
        String firstName,

        @Schema(description = "Numele de familie al doctorului", example = "Popescu")
        String lastName,

        @Schema(description = "Numele complet", example = "Mihai Popescu")
        String fullName,

        @Schema(description = "Specialitatea medicală", example = "Cardiologie")
        String specialty
) {}
