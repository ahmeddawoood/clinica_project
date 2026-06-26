package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.dto.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class WebSocketNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SimpMessagingTemplate messaging;
    private final AppNotificationService appNotificationService;

    public WebSocketNotificationService(SimpMessagingTemplate messaging,
                                        AppNotificationService appNotificationService) {
        this.messaging = messaging;
        this.appNotificationService = appNotificationService;
    }

    public void notifyPatient(Appointment appt, String type, String message) {
        String actor = "Dr. " + appt.getDoctor().getFullName();
        send("/topic/patient/" + appt.getPatient().getId() + "/notifications",
                type, appt.getId(), message, actor);
        try {
            appNotificationService.createForUser(
                    appt.getPatient().getUser(), type, message, appt.getId(), actor);
        } catch (Exception e) {
            log.warn("Failed to persist notification for patient: {}", e.getMessage());
        }
    }

    public void notifyDoctor(Appointment appt, String type, String message) {
        String actor = appt.getPatient().getFullName();
        send("/topic/doctor/" + appt.getDoctor().getId() + "/notifications",
                type, appt.getId(), message, actor);
        try {
            appNotificationService.createForUser(
                    appt.getDoctor().getUser(), type, message, appt.getId(), actor);
        } catch (Exception e) {
            log.warn("Failed to persist notification for doctor: {}", e.getMessage());
        }
    }

    private void send(String topic, String type, Long apptId, String message, String actor) {
        try {
            NotificationMessage notif = new NotificationMessage(
                    type, apptId, message, actor,
                    LocalDateTime.now().format(FMT));
            messaging.convertAndSend(topic, notif);
            log.debug("WS -> {} [{}]", topic, type);
        } catch (Exception e) {
            log.warn("Failed to send WS notification to {}: {}", topic, e.getMessage());
        }
    }
}
