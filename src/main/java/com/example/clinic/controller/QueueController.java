package com.example.clinic.controller;

import com.example.clinic.domain.Doctor;
import com.example.clinic.service.DoctorService;
import com.example.clinic.service.IntelligentAssignmentService;
import com.example.clinic.service.SmartQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Controller
public class QueueController {

    private final SmartQueueService queueService;
    private final IntelligentAssignmentService assignmentService;
    private final DoctorService doctorService;

    public QueueController(SmartQueueService queueService,
                            IntelligentAssignmentService assignmentService,
                            DoctorService doctorService) {
        this.queueService = queueService;
        this.assignmentService = assignmentService;
        this.doctorService = doctorService;
    }

    @GetMapping("/doctor/queue")
    public String queuePage(Model model, Principal principal) {
        Doctor doctor = doctorService.getDoctorByEmail(principal.getName());
        List<SmartQueueService.QueueEntry> queue = queueService.getTodayQueue(doctor);
        IntStream.range(0, queue.size()).forEach(i -> queue.get(i).setPosition(i + 1));

        long urgentCount    = queue.stream().filter(e -> "CRITICAL".equals(e.getAppointment().getPriority()) || "HIGH".equals(e.getAppointment().getPriority())).count();
        long pendingCount   = queue.stream().filter(e -> "PENDING".equals(e.getAppointment().getStatus())).count();
        long confirmedCount = queue.stream().filter(e -> "CONFIRMED".equals(e.getAppointment().getStatus())).count();

        model.addAttribute("doctor", doctor);
        model.addAttribute("queue", queue);
        model.addAttribute("stats", queueService.getSystemStats());
        model.addAttribute("urgentCount",    urgentCount);
        model.addAttribute("pendingCount",   pendingCount);
        model.addAttribute("confirmedCount", confirmedCount);
        return "doctor/queue";
    }

    @GetMapping("/api/assign")
    @ResponseBody
    @Tag(name = "Queue & AI",
         description = "Coadă inteligentă și recomandare doctor prin algoritm de scoring")
    @Operation(
        summary = "Recomandare inteligentă doctor",
        description = """
                Returnează o listă ordonată de doctori recomandați pe baza unui **algoritm de scoring** care ia în considerare:
                - Numărul de programări existente pentru ziua respectivă (disponibilitate)
                - Specialitatea cerută (filtrare)
                - Timpul estimat de așteptare
                - Scorul de disponibilitate calculat dinamic

                Fiecare element din răspuns conține:
                - `doctorId`, `name`, `specialty`
                - `score` - scorul de recomandare (mai mare = mai disponibil)
                - `appointmentsToday` - numărul de programări din ziua respectivă
                - `waitMinutes` - timp estimat de așteptare în minute
                - `availability` - eticheta de disponibilitate (ex. "Disponibil", "Aglomerat")

                Folosit de formularul de programare pentru a sugera cel mai potrivit doctor.
                """
    )
    @ApiResponse(responseCode = "200", description = "Listă doctori recomandați, ordonată după scor",
        content = @Content(mediaType = "application/json"))
    public ResponseEntity<List<Map<String, Object>>> suggestDoctors(
            @Parameter(description = "Specialitatea medicală dorită (ex. `Cardiologie`). Opțional - fără filtru returnează toți doctorii.",
                       example = "Cardiologie")
            @RequestParam(required = false) String specialty,
            @Parameter(description = "Data și ora dorită în format ISO-8601 (ex. `2026-05-20T10:00`). Implicit: ora curentă + 1h.",
                       example = "2026-05-20T10:00")
            @RequestParam(required = false) String dateTime) {

        LocalDateTime requestedTime;
        try {
            requestedTime = dateTime != null ? LocalDateTime.parse(dateTime) : LocalDateTime.now().plusHours(1);
        } catch (Exception e) {
            requestedTime = LocalDateTime.now().plusHours(1);
        }

        List<Map<String, Object>> result = assignmentService.rankDoctors(specialty, requestedTime)
                .stream()
                .map(rec -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("doctorId",          rec.getDoctor().getId());
                    m.put("name",              "Dr. " + rec.getDoctor().getFullName());
                    m.put("specialty",         rec.getDoctor().getSpecialty());
                    m.put("score",             rec.getScore());
                    m.put("appointmentsToday", rec.getAppointmentsToday());
                    m.put("waitMinutes",       rec.getEstimatedWaitMinutes());
                    m.put("availability",      rec.getAvailabilityLabel());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/queue/stats")
    @ResponseBody
    @Tag(name = "Queue & AI")
    @Operation(
        summary = "Statistici coadă globală",
        description = """
                Returnează distribuția programărilor active pe niveluri de prioritate AI:
                - `total` - numărul total de programări în coadă azi
                - `critical` - programări cu prioritate CRITICAL (urgențe medicale)
                - `high` - programări cu prioritate HIGH
                - `medium` - programări cu prioritate MEDIUM
                - `low` - programări cu prioritate LOW

                Prioritatea este setată automat de **modulul AI** (DeepSeek) la momentul programării,
                pe baza analizei simptomelor descrise de pacient.
                """
    )
    @ApiResponse(responseCode = "200", description = "Statistici coadă returnate",
        content = @Content(mediaType = "application/json"))
    public ResponseEntity<Map<String, Object>> queueStats() {
        SmartQueueService.QueueStats s = queueService.getSystemStats();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total",    s.getTotal());
        m.put("critical", s.getCritical());
        m.put("high",     s.getHigh());
        m.put("medium",   s.getMedium());
        m.put("low",      s.getLow());
        return ResponseEntity.ok(m);
    }
}
