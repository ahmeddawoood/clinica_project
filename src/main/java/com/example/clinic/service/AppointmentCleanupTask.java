package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AppointmentCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCleanupTask.class);

    private final AppointmentRepository appointmentRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    public AppointmentCleanupTask(AppointmentRepository appointmentRepository,
                                   AuditLogService auditLogService,
                                   NotificationService notificationService,
                                   ActivityLogService activityLogService) {
        this.appointmentRepository = appointmentRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
    }
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void cancelExpiredUnpaidAppointments() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        List<Appointment> expired = appointmentRepository
                .findByStatusAndCreatedAtBefore("PENDING_PAYMENT", cutoff);

        if (expired.isEmpty()) return;

        for (Appointment appt : expired) {
            appt.setStatus("CANCELLED");
            appt.setCancelReason("Anulat automat: taxa de rezervare nu a fost achitată în termen de 1 oră.");
            appointmentRepository.save(appt);
            auditLogService.log(
                    "system",
                    "AUTO_CANCEL",
                    "Programare #" + appt.getId() + " anulată automat - neplătită în 1h"
                    + " | Pacient: " + appt.getPatient().getFullName()
                    + " | Doctor: Dr. " + appt.getDoctor().getFullName()
            );
            log.info("Auto-cancelled unpaid appointment id={} patient={} doctor={}",
                    appt.getId(), appt.getPatient().getFullName(), appt.getDoctor().getFullName());
            notificationService.notifyAppointmentCancelled(appt, appt.getCancelReason());
            activityLogService.saveLog(appt.getId(), "APPOINTMENT_CANCELLED",
                    "Anulată automat - taxa de rezervare neachitată în 1 oră");
        }

        log.info("Cleanup task: {} appointment(s) auto-cancelled for non-payment", expired.size());
    }
}
