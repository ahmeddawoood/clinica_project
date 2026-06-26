package com.example.clinic.controller;

import com.example.clinic.repository.AppointmentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NotificationAdvice {

    private final AppointmentRepository appointmentRepository;

    public NotificationAdvice(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @ModelAttribute
    public void addNotifications(Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated()) return;
        String email = auth.getName();
        boolean isDoctor  = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
        boolean isPatient = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));

        if (isDoctor) {
            long pending = appointmentRepository.countPendingByDoctorEmail(email);
            model.addAttribute("navBadge", pending > 0 ? pending : null);
        } else if (isPatient) {
            long pending = appointmentRepository.countPendingByPatientEmail(email);
            long pendingPayment = appointmentRepository.countPendingPaymentByPatientEmail(email);
            long totalNotifications = pending + pendingPayment;

            model.addAttribute("navPending", pending);
            model.addAttribute("navPendingPayment", pendingPayment);
            model.addAttribute("navBadge", totalNotifications > 0 ? totalNotifications : null);
        }
    }
}
