package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AppointmentReminderTask {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderTask.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    public AppointmentReminderTask(AppointmentRepository appointmentRepository,
                                    NotificationService notificationService) {
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
    }
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyReminders() {
        LocalDateTime windowStart = LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay();
        LocalDateTime windowEnd   = windowStart.plusDays(1);

        List<Appointment> upcoming = appointmentRepository
                .findByStatusAndAppointmentDateBetween("CONFIRMED", windowStart, windowEnd);

        if (upcoming.isEmpty()) {
            log.debug("Reminder task: no confirmed appointments tomorrow");
            return;
        }

        for (Appointment appt : upcoming) {
            notificationService.notifyAppointmentReminder(appt);
        }
        log.info("Reminder task: sent {} reminder(s) for tomorrow's appointments", upcoming.size());
    }
}
