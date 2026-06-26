package com.example.clinic.service;

import com.example.clinic.domain.ActivityLog;

import java.util.List;

public interface ActivityLogService {
    void saveLog(Long appointmentId, String action, String description);
    List<ActivityLog> getTimeline(Long appointmentId);
}
