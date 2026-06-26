package com.example.clinic.repository;

import com.example.clinic.domain.Message;
import com.example.clinic.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.receiver WHERE m.receiver = :receiver ORDER BY m.sentAt DESC")
    List<Message> findByReceiverOrderBySentAtDesc(@Param("receiver") User receiver);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.receiver WHERE m.sender = :sender ORDER BY m.sentAt DESC")
    List<Message> findBySenderOrderBySentAtDesc(@Param("sender") User sender);
    @Query("SELECT m FROM Message m JOIN FETCH m.sender JOIN FETCH m.receiver WHERE m.id = :id")
    Optional<Message> findByIdWithSenderReceiver(@Param("id") Long id);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiver = :user AND m.isRead = false")
    long countUnread(@Param("user") User user);

    @Query("SELECT m FROM Message m WHERE (m.sender = :u OR m.receiver = :u) ORDER BY m.sentAt DESC")
    List<Message> findAllForUser(@Param("u") User user);
}
