package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.dto.ProfileForm;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.service.AppNotificationService;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.DoctorService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import com.example.clinic.service.UserService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Doctori",
        description = "Confirmare / anulare programări, consultații, coadă pacienți, rețete")
@Controller
@RequestMapping("/doctor")
public class DoctorController {

    private static final Logger log = LoggerFactory.getLogger(DoctorController.class);

    private final DoctorService doctorService;
    private final StripeService stripeService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final AppNotificationService appNotificationService;

    public DoctorController(DoctorService doctorService,
                             StripeService stripeService,
                             AuditLogService auditLogService,
                             NotificationService notificationService,
                             UserService userService,
                             AppNotificationService appNotificationService) {
        this.doctorService = doctorService;
        this.stripeService = stripeService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.appNotificationService = appNotificationService;
    }

    @org.springframework.web.bind.annotation.ModelAttribute
    public void addNotificationCount(Model model, Principal principal) {
        if (principal != null) {
            try {
                var doctor = doctorService.getDoctorByEmail(principal.getName());
                model.addAttribute("unreadNotifCount",
                        appNotificationService.countUnread(doctor.getUser()));
            } catch (Exception ignored) {}
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String email = principal.getName();
        var doctor       = doctorService.getDoctorByEmail(email);
        var appointments = doctorService.getAppointments(email);
        var allRatings   = doctorService.getAllRatings(email);
        var monthlyStats = doctorService.getMonthlyStats(email, 6);

        long pending        = appointments.stream().filter(a -> "PENDING".equals(a.getStatus())).count();
        long confirmed      = appointments.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).count();
        long completed      = appointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count();
        long uniquePatients = appointments.stream().map(a -> a.getPatient().getId()).distinct().count();

        var uniquePatientMap = new LinkedHashMap<Long, com.example.clinic.domain.Patient>();
        appointments.forEach(a -> uniquePatientMap.putIfAbsent(a.getPatient().getId(), a.getPatient()));

        model.addAttribute("doctor",                  doctor);
        model.addAttribute("appointments",            appointments);
        model.addAttribute("todayAppointments",       doctorService.getTodayAppointments(email));
        model.addAttribute("pendingCount",            pending);
        model.addAttribute("confirmedCount",          confirmed);
        model.addAttribute("completedCount",          completed);
        model.addAttribute("uniquePatients",          uniquePatients);
        model.addAttribute("totalAppointments",       appointments.size());
        model.addAttribute("monthlyLabels",           new ArrayList<>(monthlyStats.keySet()));
        model.addAttribute("monthlyData",             new ArrayList<>(monthlyStats.values()));
        model.addAttribute("avgRating",               doctorService.getAverageRating(email));
        model.addAttribute("ratingCount",             doctorService.getRatingCount(email));
        model.addAttribute("pendingAppointmentsList", appointments.stream().filter(a -> "PENDING".equals(a.getStatus())).toList());
        model.addAttribute("confirmedAppointmentsList", appointments.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).toList());
        model.addAttribute("completedAppointmentsList", appointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).toList());
        model.addAttribute("uniquePatientsList",      uniquePatientMap.values().stream().toList());
        model.addAttribute("allRatings",              allRatings);
        model.addAttribute("recentRatings",           allRatings.stream().limit(5).toList());
        return "doctor/dashboard";
    }

    @GetMapping("/profile")
    public String profileForm(Model model, Principal principal) {
        String email = principal.getName();
        var doctor = doctorService.getDoctorByEmail(email);
        ProfileForm form = new ProfileForm();
        form.setFirstName(doctor.getFirstName());
        form.setLastName(doctor.getLastName());
        form.setPhone(doctor.getPhone());
        form.setSpecialty(doctor.getSpecialty());
        model.addAttribute("doctor", doctor);
        model.addAttribute("form", form);
        model.addAttribute("avgRating",   doctorService.getAverageRating(email));
        model.addAttribute("ratingCount", doctorService.getRatingCount(email));
        model.addAttribute("recentRatings", doctorService.getAllRatings(email).stream().limit(10).toList());
        return "doctor/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("form") ProfileForm form,
                                BindingResult result,
                                Principal principal,
                                Model model,
                                RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("doctor", doctorService.getDoctorByEmail(principal.getName()));
            return "doctor/profile";
        }
        doctorService.updateProfile(principal.getName(), form);
        ra.addFlashAttribute("success", "Profilul a fost actualizat cu succes.");
        return "redirect:/doctor/profile";
    }

    @PostMapping("/confirm/{id}")
    public String confirm(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        Appointment appt = findAndVerifyOwnership(id, principal.getName());
        try {
            doctorService.confirmAppointment(id);
            ra.addFlashAttribute("success", "Programare confirmată cu succes.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/doctor/dashboard";
    }

    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        Appointment appt = findAndVerifyOwnership(id, principal.getName());
        boolean hadPayment     = appt.isBookingFeePaid();
        String paymentIntentId = appt.getStripePaymentIntentId();

        doctorService.cancelAppointment(id);

        if (hadPayment && paymentIntentId != null
                && stripeService.isEnabled()
                && !paymentIntentId.startsWith("local_demo_")) {
            try {
                stripeService.refund(paymentIntentId);
                auditLogService.log(principal.getName(), "REFUND_PROCESSED",
                        "Refund processed for appointment #" + id);
                ra.addFlashAttribute("info", "Programare anulată. Taxa de rezervare a fost rambursată.");
            } catch (Exception e) {
                log.error("Stripe refund failed for appointment #{}: {}", id, e.getMessage());
                ra.addFlashAttribute("info", "Programare anulată. Rambursarea necesită procesare manuală.");
            }
        } else {
            ra.addFlashAttribute("info", "Programare anulată.");
        }
        return "redirect:/doctor/dashboard";
    }

    @PostMapping("/complete/{id}")
    public String complete(@PathVariable Long id,
                           @RequestParam(required = false) String notes,
                           Principal principal,
                           RedirectAttributes ra) {
        findAndVerifyOwnership(id, principal.getName());
        doctorService.completeAppointment(id, notes);
        ra.addFlashAttribute("success", "Programare finalizată cu succes.");
        return "redirect:/doctor/dashboard";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal,
                                 RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("pwdError", "Parolele noi nu coincid.");
            return "redirect:/doctor/profile";
        }
        if (newPassword.length() < 6) {
            ra.addFlashAttribute("pwdError", "Parola nouă trebuie să aibă cel puțin 6 caractere.");
            return "redirect:/doctor/profile";
        }
        try {
            userService.changePassword(principal.getName(), currentPassword, newPassword);
            ra.addFlashAttribute("pwdSuccess", "Parola a fost schimbată cu succes.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("pwdError", e.getMessage());
        }
        return "redirect:/doctor/profile";
    }

    @GetMapping("/calendar")
    public String calendarPage(Model model, Principal principal) {
        model.addAttribute("doctor", doctorService.getDoctorByEmail(principal.getName()));
        return "doctor/calendar";
    }

    @GetMapping("/calendar/events")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> calendarEvents(Principal principal) {
        var appointments = doctorService.getAppointments(principal.getName());
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        Map<String, String> statusColors = Map.of(
                "PENDING",         "#f59e0b",
                "PENDING_PAYMENT", "#94a3b8",
                "CONFIRMED",       "#22c55e",
                "COMPLETED",       "#2563eb",
                "CANCELLED",       "#ef4444"
        );

        List<Map<String, Object>> events = appointments.stream()
                .filter(a -> !"CANCELLED".equals(a.getStatus()))
                .map(a -> {
                    Map<String, Object> ev = new HashMap<>();
                    ev.put("id",    a.getId());
                    ev.put("title", a.getPatient().getFullName());
                    ev.put("start", a.getAppointmentDate().format(fmt));
                    ev.put("end",   a.getAppointmentDate().plusMinutes(30).format(fmt));
                    ev.put("color", statusColors.getOrDefault(a.getStatus(), "#64748b"));
                    ev.put("extendedProps", Map.of(
                            "status",  a.getStatus(),
                            "patient", a.getPatient().getFullName(),
                            "notes",   a.getNotes() != null ? a.getNotes() : ""
                    ));
                    return ev;
                })
                .toList();

        return ResponseEntity.ok(events);
    }

    @GetMapping("/notifications")
    public String notifications(Model model, Principal principal) {
        var doctor = doctorService.getDoctorByEmail(principal.getName());
        var notifications = appNotificationService.getNotificationsForUser(doctor.getUser());
        appNotificationService.markAllAsRead(doctor.getUser());
        model.addAttribute("doctor", doctor);
        model.addAttribute("notifications", notifications);
        return "doctor/notifications";
    }

    private Appointment findAndVerifyOwnership(Long appointmentId, String email) {
        Appointment appt = doctorService.getAppointments(email).stream()
            .filter(a -> a.getId().equals(appointmentId))
            .findFirst()
            .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if (!appt.getDoctor().getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("Nu aveți permisiunea de a acționa asupra acestei programări.");
        }
        return appt;
    }
}
