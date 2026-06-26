package com.example.clinic.controller;

import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Rating;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.dto.ProfileForm;
import com.example.clinic.dto.TimelineEvent;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.repository.MedicalReportRepository;
import com.example.clinic.repository.RatingRepository;
import com.example.clinic.service.ActivityLogService;
import com.example.clinic.service.AiService;
import com.example.clinic.service.AppNotificationService;
import com.example.clinic.service.InvoicePdfService;
import com.example.clinic.service.MedicalRecordService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.PatientService;
import com.example.clinic.service.PrescriptionService;
import com.example.clinic.service.StripeService;
import com.example.clinic.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Pacienți",
        description = "Dashboard, programări, profil, rețete, facturi PDF")
@Controller
@RequestMapping("/patient")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final PatientService patientService;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final RatingRepository ratingRepository;
    private final MedicalReportRepository medicalReportRepository;
    private final AiService aiService;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final InvoicePdfService invoicePdfService;
    private final MedicalRecordService medicalRecordService;
    private final PrescriptionService prescriptionService;
    private final UserService userService;
    private final AppNotificationService appNotificationService;

    @Value("${stripe.booking-fee-cents:200}")
    private long bookingFeeCents;

    @Value("${stripe.publishable-key:}")
    private String publishableKey;

    public PatientController(PatientService patientService,
                              DoctorRepository doctorRepository,
                              AppointmentRepository appointmentRepository,
                              InvoiceRepository invoiceRepository,
                              RatingRepository ratingRepository,
                              MedicalReportRepository medicalReportRepository,
                              AiService aiService,
                              StripeService stripeService,
                              NotificationService notificationService,
                              ActivityLogService activityLogService,
                              InvoicePdfService invoicePdfService,
                              MedicalRecordService medicalRecordService,
                              PrescriptionService prescriptionService,
                              UserService userService,
                              AppNotificationService appNotificationService) {
        this.patientService = patientService;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.ratingRepository = ratingRepository;
        this.medicalReportRepository = medicalReportRepository;
        this.aiService = aiService;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.invoicePdfService = invoicePdfService;
        this.medicalRecordService = medicalRecordService;
        this.prescriptionService = prescriptionService;
        this.userService = userService;
        this.appNotificationService = appNotificationService;
    }

    @org.springframework.web.bind.annotation.ModelAttribute
    public void addNotificationCount(Model model, Principal principal) {
        if (principal != null) {
            try {
                var patient = patientService.getPatientByEmail(principal.getName());
                model.addAttribute("unreadNotifCount",
                        appNotificationService.countUnread(patient.getUser()));
            } catch (Exception ignored) {}
        }
    }

    @GetMapping("/invoices/{id}/download")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long id, Principal principal) {
        var invoice = invoiceRepository.findByIdWithPatient(id)
                .orElseThrow(() -> new ClinicException("Factura nu există: " + id));
        if (!invoice.getPatient().getUser().getEmail().equals(principal.getName()))
            throw new org.springframework.security.access.AccessDeniedException("Acces interzis.");
        byte[] pdf = invoicePdfService.generateInvoicePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"factura-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        Patient patient = patientService.getPatientByEmail(principal.getName());
        var appointments = patientService.getAppointments(principal.getName());

        long pending        = appointments.stream().filter(a -> "PENDING".equals(a.getStatus())).count();
        long pendingPayment = appointments.stream().filter(a -> "PENDING_PAYMENT".equals(a.getStatus())).count();
        long confirmed      = appointments.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).count();

        var nextAppointment = appointments.stream()
                .filter(a -> "CONFIRMED".equals(a.getStatus()))
                .filter(a -> a.getAppointmentDate().isAfter(java.time.LocalDateTime.now()))
                .min(java.util.Comparator.comparing(a -> a.getAppointmentDate()))
                .orElse(null);

        var ratedIds = appointments.stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .filter(a -> ratingRepository.findByAppointment(a).isPresent())
                .map(a -> a.getId())
                .collect(Collectors.toSet());

        model.addAttribute("patient", patient);
        model.addAttribute("appointments", appointments);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("pendingPaymentCount", pendingPayment);
        model.addAttribute("confirmedCount", confirmed);
        model.addAttribute("totalAppointments", appointments.size());
        model.addAttribute("nextAppointment", nextAppointment);
        model.addAttribute("ratedIds", ratedIds);
        model.addAttribute("pendingPaymentAppointments", appointments.stream()
                .filter(a -> "PENDING_PAYMENT".equals(a.getStatus())).toList());
        model.addAttribute("pendingAppointments", appointments.stream()
                .filter(a -> "PENDING".equals(a.getStatus())).toList());
        model.addAttribute("confirmedAppointments", appointments.stream()
                .filter(a -> "CONFIRMED".equals(a.getStatus())).toList());
        return "patient/dashboard";
    }

    @GetMapping("/profile")
    public String profileForm(Model model, Principal principal) {
        Patient patient = patientService.getPatientByEmail(principal.getName());
        ProfileForm form = new ProfileForm();
        form.setFirstName(patient.getFirstName());
        form.setLastName(patient.getLastName());
        form.setPhone(patient.getPhone());
        form.setGender(patient.getGender());
        form.setDateOfBirth(patient.getDateOfBirth());
        form.setBloodType(patient.getBloodType());
        form.setAllergies(patient.getAllergies());
        form.setAddress(patient.getAddress());
        model.addAttribute("patient", patient);
        model.addAttribute("form", form);
        return "patient/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("form") ProfileForm form,
                                BindingResult result,
                                Principal principal,
                                Model model,
                                RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("patient", patientService.getPatientByEmail(principal.getName()));
            return "patient/profile";
        }
        patientService.updateProfile(principal.getName(), form);
        ra.addFlashAttribute("success", "Profilul a fost actualizat cu succes.");
        return "redirect:/patient/profile";
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal,
                                 RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("pwdError", "Parolele noi nu coincid.");
            return "redirect:/patient/profile";
        }
        if (newPassword.length() < 6) {
            ra.addFlashAttribute("pwdError", "Parola nouă trebuie să aibă cel puțin 6 caractere.");
            return "redirect:/patient/profile";
        }
        try {
            userService.changePassword(principal.getName(), currentPassword, newPassword);
            ra.addFlashAttribute("pwdSuccess", "Parola a fost schimbată cu succes.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("pwdError", e.getMessage());
        }
        return "redirect:/patient/profile";
    }

    @GetMapping("/book")
    public String bookPage(Model model, Principal principal, @RequestParam(required = false) String specialty) {
        var doctors = specialty != null && !specialty.isEmpty()
                ? doctorRepository.findAll().stream()
                    .filter(d -> specialty.equalsIgnoreCase(d.getSpecialty()))
                    .toList()
                : doctorRepository.findAll();
        model.addAttribute("patient", patientService.getPatientByEmail(principal.getName()));
        model.addAttribute("doctors", doctors);
        model.addAttribute("form", new AppointmentForm());
        model.addAttribute("specialty", specialty);
        model.addAttribute("specialties", doctorRepository.findAll().stream()
                .map(d -> d.getSpecialty())
                .filter(s -> s != null && !s.isBlank())
                .distinct().sorted().toList());
        return "patient/book";
    }

    @PostMapping("/book")
    public String book(@ModelAttribute("form") AppointmentForm form,
                       Principal principal,
                       RedirectAttributes ra) {
        try {
            Long appointmentId = patientService.bookAppointment(form, principal.getName());
            return "redirect:/patient/appointments/" + appointmentId + "/pay";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/patient/book";
        }
    }
    @GetMapping("/appointments/{id}/pay")
    public String payPage(@PathVariable Long id, Model model, Principal principal) {
        var appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
        verifyPatientOwns(appointment, principal.getName());
        if (appointment.isBookingFeePaid()) {
            return "redirect:/patient/dashboard";
        }
        model.addAttribute("appointment", appointment);
        model.addAttribute("patient", patientService.getPatientByEmail(principal.getName()));
        model.addAttribute("stripeEnabled", stripeService.isEnabled());
        model.addAttribute("stripePublishableKey", publishableKey);
        model.addAttribute("stripeReady", stripeService.isEnabled()
                && publishableKey != null
                && !publishableKey.isBlank());
        model.addAttribute("bookingFeeCents", bookingFeeCents);
        return "patient/payment";
    }

    @GetMapping("/appointments/{id}/pay/success")
    public String legacyPaySuccessRedirect(@PathVariable Long id,
                                           @RequestParam(name = "session_id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "redirect:/payment-success?appointmentId=" + id;
        }
        return "redirect:/payment-success?appointmentId=" + id + "&session_id=" + sessionId;
    }

    @GetMapping("/appointments/{id}/pay/failed")
    public String legacyPayFailedRedirect(@PathVariable Long id) {
        return "redirect:/payment-cancel?appointmentId=" + id;
    }

    @PostMapping("/appointments/{id}/rate")
    public String rateAppointment(@PathVariable Long id,
                                  @RequestParam int stars,
                                  @RequestParam(required = false) String comment,
                                  Principal principal,
                                  RedirectAttributes ra) {
        var appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
        Patient patient = patientService.getPatientByEmail(principal.getName());
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            ra.addFlashAttribute("error", "Nu poți evalua o programare care nu îți aparține.");
            return "redirect:/patient/dashboard";
        }
        if (!"COMPLETED".equals(appointment.getStatus())) {
            ra.addFlashAttribute("error",
                    "Poți evalua doctorul doar după ce consultația a avut loc și a fost finalizată.");
            return "redirect:/patient/dashboard";
        }
        if (ratingRepository.findByAppointment(appointment).isPresent()) {
            ra.addFlashAttribute("error", "Ai evaluat deja această consultație.");
            return "redirect:/patient/dashboard";
        }
        if (stars < 1 || stars > 5) {
            ra.addFlashAttribute("error", "Nota trebuie să fie între 1 și 5.");
            return "redirect:/patient/dashboard";
        }

        Rating rating = new Rating();
        rating.setAppointment(appointment);
        rating.setDoctor(appointment.getDoctor());
        rating.setPatient(patient);
        rating.setStars(stars);
        rating.setComment(comment);
        ratingRepository.save(rating);
        ra.addFlashAttribute("success",
                "Mulțumim pentru evaluare! Nota ta a fost adăugată profilului doctorului.");
        return "redirect:/patient/dashboard";
    }

    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String cancelReason,
                         Principal principal,
                         RedirectAttributes ra) {
        var appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
        verifyPatientOwns(appointment, principal.getName());
        patientService.cancelAppointment(id, cancelReason);
        ra.addFlashAttribute("info", "Programarea a fost anulată.");
        return "redirect:/patient/dashboard";
    }

    @GetMapping("/ai/recommend")
    @ResponseBody
    @Tag(name = "Queue & AI")
    @Operation(
        summary = "Recomandare specialitate medicală prin AI",
        description = """
                Analizează simptomele descrise de pacient prin **DeepSeek AI** și returnează:
                - `specialty` - specialitatea medicală recomandată (ex. "Cardiologie")
                - `doctors` - lista doctorilor disponibili în acea specialitate
                - `found` - numărul de doctori găsiți
                - `priority` - nivelul de urgență: `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`
                - `urgent` - `true` dacă necesită atenție imediată
                - `urgencyReason` - motivul urgenței (ex. "Durere în piept + dificultăți respiratorii")
                - `confidencePercent` - procentul de încredere al analizei AI (0-100)

                Dacă AI-ul nu este configurat (lipsă `DEEPSEEK_API_KEY`), se folosește un fallback
                bazat pe cuvinte cheie pentru a determina specialitatea potrivită.
                """,
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponse(responseCode = "200", description = "Analiză AI returnată",
        content = @Content(mediaType = "application/json"))
    public Map<String, Object> aiRecommend(
            @Parameter(description = "Simptomele descrise de pacient în text liber",
                       example = "Dureri de cap frecvente, amețeli și oboseală", required = true)
            @RequestParam String symptoms) {
        var allDoctors = doctorRepository.findAll();
        List<String> specialties = allDoctors.stream()
                .map(d -> d.getSpecialty())
                .filter(s -> s != null && !s.isBlank())
                .distinct().sorted().toList();

        var analysis   = aiService.analyzeSymptoms(symptoms);
        String aiSpecialty = aiService.recommendSpecialty(symptoms, specialties);
        String resolvedSpecialty = analysis.isUrgent()
                ? analysis.getSuggestedSpecialty()
                : aiSpecialty;
        String aiCanonical = canonicalSpecialty(resolvedSpecialty);

        List<String> matchingDoctors = allDoctors.stream()
                .filter(d -> canonicalSpecialty(d.getSpecialty()).equals(aiCanonical))
                .map(d -> "Dr. " + d.getFullName() + " - " + d.getSpecialty())
                .toList();

        String uiSpecialty = allDoctors.stream()
                .map(d -> d.getSpecialty())
                .filter(sp -> canonicalSpecialty(sp).equals(aiCanonical))
                .findFirst()
                .orElse(resolvedSpecialty);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("specialty",         uiSpecialty);
        result.put("doctors",           matchingDoctors);
        result.put("found",             matchingDoctors.size());
        result.put("priority",          analysis.getPriority());
        result.put("urgent",            analysis.isUrgent());
        result.put("urgencyReason",     analysis.getUrgencyReason() != null ? analysis.getUrgencyReason() : "");
        result.put("confidencePercent", analysis.getConfidencePercent());
        return result;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z]", "");
    }

    private String canonicalSpecialty(String specialty) {
        String n = normalize(specialty);
        if (n.contains("cardio"))   return "cardiolog";
        if (n.contains("derm"))     return "dermatolog";
        if (n.contains("neuro"))    return "neurolog";
        if (n.contains("general") || n.equals("medicgeneral")) return "medic_general";
        if (n.contains("pediatr")) return "pediatru";
        return n;
    }

    @GetMapping("/history")
    public String medicalHistory(Model model, Principal principal) {
        Patient patient = patientService.getPatientByEmail(principal.getName());
        var records      = medicalRecordService.getRecordsByPatientEmail(principal.getName());
        var prescriptions = prescriptionService.getPrescriptionsByPatientEmail(principal.getName());
        var reports      = medicalReportRepository.findByPatientOrderByGeneratedAtDesc(patient);
        var invoices     = invoiceRepository.findByPatientOrderByIssuedAtDesc(patient);
        List<TimelineEvent> timeline = new ArrayList<>();
        records.forEach(r -> timeline.add(new TimelineEvent(
                r.getCreatedAt(), "RECORD",
                r.getTitle() != null ? r.getTitle() : "Fișă medicală",
                "Dr. " + r.getDoctor().getFullName(),
                r.getDiagnosis(), r.getId())));
        prescriptions.forEach(rx -> timeline.add(new TimelineEvent(
                rx.getIssuedAt(), "PRESCRIPTION",
                "Rețetă #" + rx.getId(),
                "Dr. " + rx.getDoctor().getFullName(),
                rx.getDiagnosis(), rx.getId())));
        reports.forEach(rep -> timeline.add(new TimelineEvent(
                rep.getGeneratedAt(), "REPORT",
                "Raport medical #" + rep.getId(),
                "Dr. " + rep.getDoctor().getFullName(),
                rep.getDiagnosis(), rep.getId())));
        patientService.getAppointments(principal.getName()).stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .forEach(a -> timeline.add(new TimelineEvent(
                        a.getAppointmentDate(), "APPOINTMENT",
                        "Consultație finalizată",
                        "Dr. " + a.getDoctor().getFullName(),
                        a.getDoctor().getSpecialty(), a.getId())));
        timeline.sort(Comparator.comparing(TimelineEvent::getDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        model.addAttribute("patient",       patient);
        model.addAttribute("records",       records);
        model.addAttribute("prescriptions", prescriptions);
        model.addAttribute("reports",       reports);
        model.addAttribute("invoices",      invoices);
        model.addAttribute("totalRecords",      records.size());
        model.addAttribute("totalPrescriptions", prescriptions.size());
        model.addAttribute("totalReports",   reports.size());
        model.addAttribute("timeline",       timeline);
        return "patient/history";
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id, Principal principal) {
        var report = medicalReportRepository.findById(id)
                .orElseThrow(() -> new com.example.clinic.exception.AppointmentNotFoundException(id));
        if (!report.getPatient().getUser().getEmail().equals(principal.getName())) {
            throw new org.springframework.security.access.AccessDeniedException("Nu aveți acces la acest raport medical.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"medical-report-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(report.getPdfData());
    }

    @GetMapping("/doctors")
    public String doctorsList(Model model, Principal principal,
                              @RequestParam(required = false) String specialty) {
        var allDoctors = doctorRepository.findAll();
        var displayed  = specialty != null && !specialty.isBlank()
                ? allDoctors.stream().filter(d -> specialty.equalsIgnoreCase(d.getSpecialty())).toList()
                : allDoctors;

        var doctorCards = displayed.stream().map(d -> {
            double avg   = ratingRepository.averageByDoctor(d);
            long   count = ratingRepository.countByDoctor(d);
            return Map.of("doctor", d, "avgRating", avg, "ratingCount", count);
        }).toList();

        model.addAttribute("patient",     patientService.getPatientByEmail(principal.getName()));
        model.addAttribute("doctorCards", doctorCards);
        model.addAttribute("specialties", doctorRepository.findAllSpecialties());
        model.addAttribute("selectedSpecialty", specialty != null ? specialty : "");
        return "patient/doctors";
    }

    @GetMapping("/doctors/{id}")
    public String doctorProfile(@PathVariable Long id, Model model, Principal principal) {
        var doctor  = doctorRepository.findById(id)
                .orElseThrow(() -> new ClinicException("Doctorul nu există."));
        var reviews = ratingRepository.findByDoctorOrderByCreatedAtDesc(doctor);
        double avg  = ratingRepository.averageByDoctor(doctor);
        long count  = ratingRepository.countByDoctor(doctor);
        java.util.Map<Integer, Long> starCounts = new java.util.LinkedHashMap<>();
        for (int s = 5; s >= 1; s--) {
            final int star = s;
            starCounts.put(star, reviews.stream().filter(r -> r.getStars() == star).count());
        }

        model.addAttribute("patient",     patientService.getPatientByEmail(principal.getName()));
        model.addAttribute("doctor",      doctor);
        model.addAttribute("reviews",     reviews);
        model.addAttribute("avgRating",   avg);
        model.addAttribute("ratingCount", count);
        model.addAttribute("starCounts",  starCounts);
        return "patient/doctor-profile";
    }

    @GetMapping("/notifications")
    public String notifications(Model model, Principal principal) {
        var patient = patientService.getPatientByEmail(principal.getName());
        var notifications = appNotificationService.getNotificationsForUser(patient.getUser());
        appNotificationService.markAllAsRead(patient.getUser());
        model.addAttribute("patient", patient);
        model.addAttribute("notifications", notifications);
        return "patient/notifications";
    }

    private void verifyPatientOwns(com.example.clinic.domain.Appointment appointment, String email) {
        if (!appointment.getPatient().getUser().getEmail().equals(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Nu aveți permisiunea de a accesa această programare.");
        }
    }
}
