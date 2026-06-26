package com.example.clinic.service;

import com.example.clinic.domain.AuditLog;
import com.example.clinic.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void log(String actorEmail, String action, String details) {
        auditLogRepository.save(new AuditLog(actorEmail, action, details));
    }

    @Override
    public List<AuditLog> getRecent() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }
}
