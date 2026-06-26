package com.example.clinic.service;

import com.example.clinic.domain.AppNotification;
import com.example.clinic.domain.User;
import com.example.clinic.repository.AppNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppNotificationServiceImpl implements AppNotificationService {

    private final AppNotificationRepository repo;

    public AppNotificationServiceImpl(AppNotificationRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void createForUser(User user, String type, String message, Long appointmentId, String actorName) {
        AppNotification notif = new AppNotification();
        notif.setUser(user);
        notif.setType(type);
        notif.setMessage(message);
        notif.setAppointmentId(appointmentId);
        notif.setActorName(actorName);
        notif.setCreatedAt(LocalDateTime.now());
        notif.setRead(false);
        repo.save(notif);
    }

    @Override
    public List<AppNotification> getNotificationsForUser(User user) {
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    @Override
    public long countUnread(User user) {
        return repo.countByUserAndReadFalse(user);
    }

    @Override
    @Transactional
    public void markAllAsRead(User user) {
        repo.markAllAsReadByUser(user);
    }
}
