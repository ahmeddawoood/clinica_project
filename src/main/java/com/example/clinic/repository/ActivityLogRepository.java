package com.example.clinic.repository;

import com.example.clinic.domain.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByAppointmentIdOrderByTimestampAsc(Long appointmentId);
}
