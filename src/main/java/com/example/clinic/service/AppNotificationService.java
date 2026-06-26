package com.example.clinic.service;

import com.example.clinic.domain.AppNotification;
import com.example.clinic.domain.User;

import java.util.List;

public interface AppNotificationService {
    void createForUser(User user, String type, String message, Long appointmentId, String actorName);
    List<AppNotification> getNotificationsForUser(User user);
    long countUnread(User user);
    void markAllAsRead(User user);
}
