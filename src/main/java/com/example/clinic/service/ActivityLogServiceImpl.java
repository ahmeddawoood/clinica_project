package com.example.clinic.service;

import com.example.clinic.domain.ActivityLog;
import com.example.clinic.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogServiceImpl(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Override
    @Transactional
    public void saveLog(Long appointmentId, String action, String description) {
        activityLogRepository.save(new ActivityLog(appointmentId, action, description));
    }

    @Override
    public List<ActivityLog> getTimeline(Long appointmentId) {
        return activityLogRepository.findByAppointmentIdOrderByTimestampAsc(appointmentId);
    }
}
