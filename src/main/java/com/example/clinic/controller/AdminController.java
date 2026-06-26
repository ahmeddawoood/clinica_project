package com.example.clinic.controller;

import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.service.AnalyticsService;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.BillingService;
import com.example.clinic.service.DoctorService;
import com.example.clinic.service.PatientService;
import com.example.clinic.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin",
        description = "KPI, statistici, audit log, export CSV, gestionare utilizatori")
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AnalyticsService analyticsService;
    private final AuditLogService auditLogService;
    private final BillingService billingService;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final UserService userService;

    public AdminController(AnalyticsService analyticsService,
                           AuditLogService auditLogService,
                           BillingService billingService,
                           AppointmentRepository appointmentRepository,
                           InvoiceRepository invoiceRepository,
                           PatientService patientService,
                           DoctorService doctorService,
                           UserService userService) {
        this.analyticsService = analyticsService;
        this.auditLogService = auditLogService;
        this.billingService = billingService;
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        var kpi = analyticsService.getAdminKpi();
        var monthlyAppointments = analyticsService.getMonthlyAppointments(6);
        var monthlyRevenue      = analyticsService.getMonthlyRevenue(6);
        var busiestHours        = analyticsService.getBusiestHours();
        var topDoctors          = analyticsService.getTopDoctors(5);
        var statusBreakdown     = analyticsService.getStatusBreakdown();
        model.addAttribute("kpi", kpi);
        model.addAttribute("totalPatients",           kpi.totalPatients());
        model.addAttribute("totalDoctors",            kpi.totalDoctors());
        model.addAttribute("totalAppointments",       kpi.totalAppointments());
        model.addAttribute("pendingAppointments",     kpi.pendingAppointments());
        model.addAttribute("confirmedAppointments",   kpi.confirmedAppointments());
        model.addAttribute("cancelledAppointments",   kpi.cancelledAppointments());
        model.addAttribute("completedAppointments",   kpi.completedAppointments());
        model.addAttribute("totalPaid",               kpi.totalRevenue());
        model.addAttribute("totalPending",            kpi.pendingRevenue());
        model.addAttribute("cancellationRate",  analyticsService.getCancellationRate());
        model.addAttribute("topDoctors",        topDoctors);
        model.addAttribute("monthLabels",  new ArrayList<>(monthlyAppointments.keySet()));
        model.addAttribute("monthValues",  new ArrayList<>(monthlyAppointments.values()));
        model.addAttribute("revenueLabels", new ArrayList<>(monthlyRevenue.keySet()));
        model.addAttribute("revenueValues", new ArrayList<>(monthlyRevenue.values()));
        model.addAttribute("hourLabels",   new ArrayList<>(busiestHours.keySet()));
        model.addAttribute("hourValues",   new ArrayList<>(busiestHours.values()));
        model.addAttribute("statusBreakdown", statusBreakdown);
        model.addAttribute("statusPending",   statusBreakdown.getOrDefault("PENDING",   0L));
        model.addAttribute("statusConfirmed", statusBreakdown.getOrDefault("CONFIRMED", 0L));
        model.addAttribute("statusCompleted", statusBreakdown.getOrDefault("COMPLETED", 0L));
        model.addAttribute("statusCancelled", statusBreakdown.getOrDefault("CANCELLED", 0L));
        model.addAttribute("auditLog",            auditLogService.getRecent().stream().limit(25).toList());
        model.addAttribute("recentAppointments",  appointmentRepository.findAllOrderByDateDesc()
                                                                        .stream().limit(10).toList());
        var allAppts = appointmentRepository.findAllOrderByDateDesc();
        model.addAttribute("patientsList",            patientService.getAllPatients().stream().limit(50).toList());
        model.addAttribute("doctorsList",             doctorService.getAllDoctors());
        model.addAttribute("appointmentsList",        allAppts.stream().limit(50).toList());
        model.addAttribute("pendingAppointmentsList", allAppts.stream()
                .filter(a -> "PENDING".equals(a.getStatus())).limit(50).toList());
        model.addAttribute("confirmedAppointmentsList", allAppts.stream()
                .filter(a -> "CONFIRMED".equals(a.getStatus())).limit(50).toList());
        model.addAttribute("pendingInvoicesList", invoiceRepository.findByStatusOrderByIssuedAtDesc("PENDING")
                .stream().limit(50).toList());
        model.addAttribute("paidInvoicesList",    invoiceRepository.findByStatusOrderByIssuedAtDesc("PAID")
                .stream().limit(50).toList());

        return "admin/dashboard";
    }

    @GetMapping("/patients")
    public String patients(Model model) {
        model.addAttribute("patients", patientService.getAllPatients());
        return "admin/patients";
    }

    @GetMapping("/doctors")
    public String doctors(Model model) {
        model.addAttribute("doctors", doctorService.getAllDoctors());
        return "admin/doctors";
    }

    @GetMapping("/appointments")
    public String appointments(Model model) {
        var all = appointmentRepository.findAllOrderByDateDesc();
        model.addAttribute("appointments", all);
        model.addAttribute("totalCount",     all.size());
        model.addAttribute("pendingCount",   all.stream().filter(a -> "PENDING".equals(a.getStatus())).count());
        model.addAttribute("confirmedCount", all.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).count());
        model.addAttribute("completedCount", all.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count());
        model.addAttribute("cancelledCount", all.stream().filter(a -> "CANCELLED".equals(a.getStatus())).count());
        return "admin/appointments";
    }

    @GetMapping("/users")
    public String users(Model model) {
        var users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("patientCount",   users.stream().filter(u -> "PATIENT".equals(u.getRole())).count());
        model.addAttribute("doctorCount",    users.stream().filter(u -> "DOCTOR".equals(u.getRole())).count());
        model.addAttribute("disabledCount",  users.stream().filter(u -> !u.isEnabled()).count());
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            userService.toggleUserEnabled(id);
            ra.addFlashAttribute("success", "Statusul contului a fost actualizat.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/export/appointments")
    public ResponseEntity<byte[]> exportAppointments() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("ID,Patient,Doctor,Specialty,Date,Status,Priority,Paid");
        appointmentRepository.findAllOrderByDateDesc().forEach(a -> pw.printf(
            "%d,%s,%s,%s,%s,%s,%s,%s%n",
            a.getId(),
            csv(a.getPatient().getFullName()),
            csv("Dr. " + a.getDoctor().getFullName()),
            csv(a.getDoctor().getSpecialty()),
            a.getAppointmentDate().toString(),
            a.getStatus(),
            a.getPriority() != null ? a.getPriority() : "",
            a.isBookingFeePaid() ? "YES" : "NO"
        ));
        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"appointments.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(bytes);
    }

    private String csv(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"")
            ? "\"" + v.replace("\"", "\"\"") + "\""
            : v;
    }
}
