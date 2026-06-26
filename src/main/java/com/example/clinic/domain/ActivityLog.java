package com.example.clinic.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log", indexes = @Index(columnList = "appointmentId"))
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long appointmentId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(length = 500)
    private String description;

    @PrePersist
    void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    public ActivityLog() {}

    public ActivityLog(Long appointmentId, String action, String description) {
        this.appointmentId = appointmentId;
        this.action = action;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getAppointmentId() { return appointmentId; }
    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getDescription() { return description; }
}
