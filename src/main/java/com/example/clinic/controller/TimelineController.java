package com.example.clinic.controller;

import com.example.clinic.domain.ActivityLog;
import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.User;
import com.example.clinic.exception.ResourceAccessDeniedException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/appointments/{id}/timeline")
@Tag(name = "Timeline",
     description = "Jurnal activitate programări - creare, plată, confirmare, finalizare, anulare")
public class TimelineController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.of("ro", "RO"));

    private final ActivityLogService activityLogService;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public TimelineController(ActivityLogService activityLogService,
                               AppointmentRepository appointmentRepository,
                               UserRepository userRepository) {
        this.activityLogService = activityLogService;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(
        summary = "Timeline activitate programare",
        description = """
                Returnează jurnalul complet de activitate al unei programări, ordonat cronologic.

                Fiecare eveniment conține:
                - **action** - codul intern (ex. `BOOKING_CREATED`, `PAYMENT_COMPLETED`)
                - **label** - eticheta în română (ex. "Programare creată", "Plată confirmată")
                - **icon** - clasa Bootstrap Icons pentru afișare vizuală
                - **color** - varianta de culoare Bootstrap (primary, success, danger, warning)
                - **timestamp** - data și ora formatată în română (ex. "15 mai 2026, 10:30")
                - **description** - detalii suplimentare despre eveniment
                """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Timeline returnat cu succes",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "200", description = "Listă goală dacă programarea nu are activitate",
            content = @Content)
    })
    public ResponseEntity<List<Map<String, Object>>> getTimeline(
            @Parameter(description = "ID-ul programării", example = "1", required = true)
            @PathVariable Long id, Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceAccessDeniedException("Nu aveți acces la această programare."));

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceAccessDeniedException("Nu aveți acces la această programare."));

        boolean patientOwner = appointment.getPatient() != null
                && appointment.getPatient().getUser() != null
                && user.getId().equals(appointment.getPatient().getUser().getId());

        boolean doctorOwner = appointment.getDoctor() != null
                && appointment.getDoctor().getUser() != null
                && user.getId().equals(appointment.getDoctor().getUser().getId());

        if (!patientOwner && !doctorOwner) {
            throw new ResourceAccessDeniedException("Nu aveți acces la această programare.");
        }
        List<ActivityLog> logs = activityLogService.getTimeline(id);
        List<Map<String, Object>> result = logs.stream()
                .map(entry -> Map.<String, Object>of(
                        "id",          entry.getId(),
                        "action",      entry.getAction(),
                        "label",       labelFor(entry.getAction()),
                        "icon",        iconFor(entry.getAction()),
                        "color",       colorFor(entry.getAction()),
                        "timestamp",   entry.getTimestamp().format(DATE_FMT),
                        "description", entry.getDescription() != null ? entry.getDescription() : ""
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    private String labelFor(String action) {
        return switch (action) {
            case "BOOKING_CREATED"         -> "Programare creată";
            case "PAYMENT_COMPLETED"       -> "Plată confirmată";
            case "PAYMENT_COMPLETED_DEMO"  -> "Plată confirmată (demo)";
            case "PAYMENT_FAILED"          -> "Plată eșuată";
            case "DOCTOR_CONFIRMED"        -> "Confirmat de medic";
            case "APPOINTMENT_COMPLETED"   -> "Consultație finalizată";
            case "APPOINTMENT_CANCELLED"   -> "Programare anulată";
            case "REFUND_PROCESSED"        -> "Rambursare procesată";
            case "REFUND_COMPLETED"        -> "Rambursare finalizată";
            case "REPORT_GENERATED"        -> "Raport generat";
            default                        -> action;
        };
    }

    private String iconFor(String action) {
        return switch (action) {
            case "BOOKING_CREATED"         -> "bi-calendar-plus";
            case "PAYMENT_COMPLETED",
                 "PAYMENT_COMPLETED_DEMO"  -> "bi-credit-card-fill";
            case "PAYMENT_FAILED"          -> "bi-credit-card-2-back";
            case "DOCTOR_CONFIRMED"        -> "bi-check-circle-fill";
            case "APPOINTMENT_COMPLETED"   -> "bi-patch-check-fill";
            case "APPOINTMENT_CANCELLED"   -> "bi-x-circle-fill";
            case "REFUND_PROCESSED",
                 "REFUND_COMPLETED"        -> "bi-arrow-counterclockwise";
            case "REPORT_GENERATED"        -> "bi-file-earmark-medical";
            default                        -> "bi-circle";
        };
    }

    private String colorFor(String action) {
        return switch (action) {
            case "BOOKING_CREATED"         -> "primary";
            case "PAYMENT_COMPLETED",
                 "PAYMENT_COMPLETED_DEMO"  -> "success";
            case "PAYMENT_FAILED"          -> "danger";
            case "DOCTOR_CONFIRMED"        -> "success";
            case "APPOINTMENT_COMPLETED"   -> "info";
            case "APPOINTMENT_CANCELLED"   -> "danger";
            case "REFUND_PROCESSED",
                 "REFUND_COMPLETED"        -> "warning";
            case "REPORT_GENERATED"        -> "secondary";
            default                        -> "secondary";
        };
    }
}
