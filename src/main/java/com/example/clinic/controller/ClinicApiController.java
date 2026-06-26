package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.dto.AppointmentResponse;
import com.example.clinic.dto.DoctorResponse;
import com.example.clinic.dto.PlatformStatsResponse;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "REST API", description = "Endpoint-uri JSON pentru integrare externă și testare Swagger")
public class ClinicApiController {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final PatientService patientService;

    public ClinicApiController(DoctorRepository doctorRepository,
                                AppointmentRepository appointmentRepository,
                                PatientRepository patientRepository,
                                PatientService patientService) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.patientService = patientService;
    }

    @GetMapping("/doctors")
    @Operation(
        summary = "Listează toți doctorii",
        description = "Returnează lista completă a doctorilor înregistrați în clinică. Endpoint public."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listă doctori returnată cu succes",
            content = @Content(schema = @Schema(implementation = DoctorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Eroare internă server", content = @Content)
    })
    public ResponseEntity<List<DoctorResponse>> listDoctors() {
        List<DoctorResponse> doctors = doctorRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/doctors/{id}")
    @Operation(
        summary = "Detalii doctor după ID",
        description = "Returnează informațiile unui doctor specific identificat prin ID."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Doctor găsit",
            content = @Content(schema = @Schema(implementation = DoctorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Doctorul nu există", content = @Content)
    })
    public ResponseEntity<DoctorResponse> getDoctor(
            @Parameter(description = "ID-ul doctorului", example = "1", required = true)
            @PathVariable Long id) {
        return doctorRepository.findById(id)
                .map(d -> ResponseEntity.ok(toDto(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/doctors/specialty/{specialty}")
    @Operation(
        summary = "Filtrare doctori după specialitate",
        description = "Returnează toți doctorii care au specialitatea specificată (case-insensitive)."
    )
    @ApiResponse(responseCode = "200", description = "Doctori filtrați",
        content = @Content(schema = @Schema(implementation = DoctorResponse.class)))
    public ResponseEntity<List<DoctorResponse>> getDoctorsBySpecialty(
            @Parameter(description = "Specialitatea medicală", example = "Cardiologie", required = true)
            @PathVariable String specialty) {
        List<DoctorResponse> result = doctorRepository.findBySpecialtyIgnoreCase(specialty)
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/appointments")
    @Operation(
        summary = "Programările pacientului autentificat",
        description = "Returnează lista completă a programărilor pentru pacientul autentificat în sesiune, " +
                      "ordonate descendent după dată.",
        security = @SecurityRequirement(name = "session")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Programări returnate",
            content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
        @ApiResponse(responseCode = "302", description = "Redirect la login (neautentificat)", content = @Content)
    })
    public ResponseEntity<List<AppointmentResponse>> myAppointments(Principal principal) {
        if (principal == null) return ResponseEntity.status(302).build();
        var appointments = patientService.getAppointments(principal.getName())
                .stream().map(this::toDto).toList();
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/appointments/{id}")
    @Operation(
        summary = "Detalii programare",
        description = "Returnează detaliile complete ale unei programări. " +
                      "Pacientul poate accesa doar propriile programări.",
        security = @SecurityRequirement(name = "session")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Programare găsită",
            content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
        @ApiResponse(responseCode = "403", description = "Acces interzis - nu este programarea ta", content = @Content),
        @ApiResponse(responseCode = "404", description = "Programarea nu există", content = @Content)
    })
    public ResponseEntity<AppointmentResponse> getAppointment(
            @Parameter(description = "ID-ul programării", example = "1", required = true)
            @PathVariable Long id,
            Principal principal) {
        return appointmentRepository.findById(id)
                .filter(a -> principal != null &&
                        a.getPatient().getUser().getEmail().equals(principal.getName()))
                .map(a -> ResponseEntity.ok(toDto(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Statistici publice ale platformei",
        description = "Returnează numărul total de doctori, pacienți, programări și lista specialităților disponibile. " +
                      "Endpoint public, nu necesită autentificare."
    )
    @ApiResponse(responseCode = "200", description = "Statistici returnate",
        content = @Content(schema = @Schema(implementation = PlatformStatsResponse.class)))
    public ResponseEntity<PlatformStatsResponse> platformStats() {
        PlatformStatsResponse stats = new PlatformStatsResponse(
                doctorRepository.count(),
                patientRepository.count(),
                appointmentRepository.count(),
                doctorRepository.findAllSpecialties()
        );
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/specialties")
    @Operation(
        summary = "Lista specialităților medicale",
        description = "Returnează lista tuturor specialităților medicale disponibile în clinică, sortate alfabetic."
    )
    @ApiResponse(responseCode = "200", description = "Specialități returnate",
        content = @Content(schema = @Schema(implementation = String.class)))
    public ResponseEntity<List<String>> specialties() {
        return ResponseEntity.ok(doctorRepository.findAllSpecialties());
    }

    private DoctorResponse toDto(Doctor d) {
        return new DoctorResponse(d.getId(), d.getFirstName(), d.getLastName(),
                d.getFullName(), d.getSpecialty());
    }

    private AppointmentResponse toDto(Appointment a) {
        return new AppointmentResponse(
                a.getId(), a.getStatus(), a.getAppointmentDate(),
                a.getPriority(), a.getNotes(),
                toDto(a.getDoctor()),
                a.getCancelReason(), a.getCreatedAt());
    }
}
