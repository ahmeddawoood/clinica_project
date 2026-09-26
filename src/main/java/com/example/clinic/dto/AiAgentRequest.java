package com.example.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record AiAgentRequest(@NotBlank(message = "Message is required") String message) {
}
