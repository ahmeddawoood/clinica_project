package com.example.clinic.dto;

public record NotificationMessage(
        String type,
        Long appointmentId,
        String message,
        String actorName,
        String timestamp
) {}
