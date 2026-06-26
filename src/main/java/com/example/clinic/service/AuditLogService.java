package com.example.clinic.service;

import com.example.clinic.domain.AuditLog;

import java.util.List;

public interface AuditLogService {
    void log(String actorEmail, String action, String details);
    List<AuditLog> getRecent();
}
