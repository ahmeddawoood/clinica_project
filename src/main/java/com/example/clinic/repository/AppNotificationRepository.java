package com.example.clinic.repository;

import com.example.clinic.domain.AppNotification;
import com.example.clinic.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findByUserOrderByCreatedAtDesc(User user);

    long countByUserAndReadFalse(User user);

    @Modifying
    @Query("UPDATE AppNotification n SET n.read = true WHERE n.user = :user")
    void markAllAsReadByUser(User user);
}
