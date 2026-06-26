package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.MedicalReport;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.service.ActivityLogService;
import com.example.clinic.service.DoctorService;
import com.example.clinic.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/doctor/report")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final AppointmentRepository appointmentRepository;
    private final DoctorService doctorService;
    private final ActivityLogService activityLogService;

    public ReportController(ReportService reportService,
                             AppointmentRepository appointmentRepository,
                             DoctorService doctorService,
                             ActivityLogService activityLogService) {
        this.reportService = reportService;
        this.appointmentRepository = appointmentRepository;
        this.doctorService = doctorService;
        this.activityLogService = activityLogService;
    }

    @GetMapping("/generate/{appointmentId}")
    public String generateForm(@PathVariable Long appointmentId,
                                Model model, Principal principal) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        verifyDoctorOwns(appt, principal.getName());

        model.addAttribute("appointment", appt);
        model.addAttribute("doctor", doctorService.getDoctorByEmail(principal.getName()));
        model.addAttribute("existingReports",
                reportService.findByAppointment(appointmentId));
        return "doctor/report-generate";
    }

    @PostMapping("/generate/{appointmentId}")
    public String generate(@PathVariable Long appointmentId,
                            @RequestParam String diagnosis,
                            @RequestParam(required = false) String treatment,
                            @RequestParam(required = false) String notes,
                            Principal principal, RedirectAttributes ra) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        verifyDoctorOwns(appt, principal.getName());

        if (diagnosis == null || diagnosis.isBlank()) {
            ra.addFlashAttribute("error", "Diagnosticul este obligatoriu.");
            return "redirect:/doctor/report/generate/" + appointmentId;
        }

        MedicalReport report = reportService.generate(appointmentId, diagnosis, treatment, notes);
        activityLogService.saveLog(appointmentId, "REPORT_GENERATED",
                "Raport medical generat de Dr. " + appt.getDoctor().getFullName()
                + " | Report #" + report.getId());

        ra.addFlashAttribute("success",
                "Raportul medical a fost generat cu succes. ID: #" + report.getId());
        return "redirect:/doctor/report/generate/" + appointmentId;
    }

    @GetMapping("/download/{reportId}")
    public ResponseEntity<byte[]> download(@PathVariable Long reportId, Principal principal) {
        MedicalReport report = reportService.findById(reportId);
        verifyDoctorOwns(report.getAppointment(), principal.getName());

        String filename = "raport-medical-" + report.getAppointment().getId()
                + "-" + report.getId() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(report.getPdfData());
    }

    @GetMapping("/list/{appointmentId}")
    @ResponseBody
    @Tag(name = "Doctori")
    @Operation(
        summary = "Rapoarte medicale ale programării",
        description = "Returnează lista fișelor medicale generate de doctor pentru o programare specifică. " +
                      "Accesibil doar de medicul care deține programarea (verificare ownership).",
        security = @SecurityRequirement(name = "cookieAuth")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista rapoartelor returnată",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "403", description = "Programarea aparține altui doctor", content = @Content),
        @ApiResponse(responseCode = "404", description = "Programarea nu există", content = @Content)
    })
    public ResponseEntity<?> list(
            @Parameter(description = "ID-ul programării", example = "1", required = true)
            @PathVariable Long appointmentId, Principal principal) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        verifyDoctorOwns(appt, principal.getName());

        var reports = reportService.findByAppointment(appointmentId).stream()
                .map(r -> java.util.Map.of(
                        "id", r.getId(),
                        "generatedAt", r.getGeneratedAt().toString(),
                        "diagnosis", r.getDiagnosis()
                ))
                .toList();
        return ResponseEntity.ok(reports);
    }

    private void verifyDoctorOwns(Appointment appt, String email) {
        if (!appt.getDoctor().getUser().getEmail().equals(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Nu aveți acces la această programare.");
        }
    }
}
