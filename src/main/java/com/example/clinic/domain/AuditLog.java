package com.example.clinic.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 1000)
    private String details;

    @PrePersist
    void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog() {}

    public AuditLog(String actorEmail, String action, String details) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.details = details;
    }

    public Long getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
}
