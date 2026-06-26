package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import java.util.Map;
public interface NotificationService {
    void sendEmail(String to, String subject, String template, Map<String, Object> vars);
    void sendSms(String phone, String body);
    void notifyBookingCreated(Appointment appointment);
    void notifyPaymentSuccess(Appointment appointment);
    void notifyAppointmentConfirmed(Appointment appointment);
    void notifyAppointmentCompleted(Appointment appointment);
    void notifyAppointmentCancelled(Appointment appointment, String reason);
    void notifyRefund(Appointment appointment);
    void notifyAppointmentReminder(Appointment appointment);
    void sendPasswordResetEmail(String to, String resetLink);
}
