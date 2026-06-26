package com.example.clinic.controller;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.DoctorSchedule;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.DoctorScheduleRepository;
import com.example.clinic.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;

@Controller
public class ScheduleController {

    private final DoctorService doctorService;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;

    public ScheduleController(DoctorService doctorService,
                               DoctorRepository doctorRepository,
                               DoctorScheduleRepository scheduleRepository,
                               AppointmentRepository appointmentRepository) {
        this.doctorService = doctorService;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/doctor/schedule")
    public String schedulePage(Model model, Principal principal) {
        Doctor doctor = doctorService.getDoctorByEmail(principal.getName());
        List<DoctorSchedule> schedules = scheduleRepository.findByDoctorOrderByDayOfWeek(doctor);
        Map<Integer, DoctorSchedule> byDay = new LinkedHashMap<>();
        for (DoctorSchedule s : schedules) byDay.put(s.getDayOfWeek(), s);

        model.addAttribute("doctor", doctor);
        model.addAttribute("byDay", byDay);
        model.addAttribute("days", dayNames());
        return "doctor/schedule";
    }

    @PostMapping("/doctor/schedule/save")
    @Transactional
    public String scheduleSave(@RequestParam Map<String, String> params,
                                Principal principal, RedirectAttributes ra) {
        Doctor doctor = doctorService.getDoctorByEmail(principal.getName());

        for (int day = 1; day <= 7; day++) {
            boolean avail = "on".equals(params.get("avail_" + day));
            int start = parseHour(params.get("start_" + day), 8);
            int end   = parseHour(params.get("end_" + day), 18);

            DoctorSchedule s = scheduleRepository
                    .findByDoctorAndDayOfWeek(doctor, day)
                    .orElseGet(DoctorSchedule::new);
            s.setDoctor(doctor);
            s.setDayOfWeek(day);
            s.setAvailable(avail);
            s.setStartHour(start);
            s.setEndHour(end);
            scheduleRepository.save(s);
        }
        ra.addFlashAttribute("success", "Programul a fost salvat.");
        return "redirect:/doctor/schedule";
    }

    @GetMapping("/api/slots/{doctorId}/{date}")
    @ResponseBody
    @Tag(name = "REST API")
    @Operation(
        summary = "Sloturi orare disponibile",
        description = """
                Returnează lista orelor libere ale unui doctor pentru o dată specificată.

                Verifică:
                - Programul doctorului (zile și ore de lucru) configurat în admin
                - Sloturile deja ocupate (programări CONFIRMED sau PENDING în ziua respectivă)

                Răspuns: listă de șiruri `HH:00` (ex. `["09:00","10:00","14:00"]`).
                Listă goală = doctorul nu lucrează în ziua respectivă sau toate sloturile sunt ocupate.
                Folosit de formularul de programare pentru selector dinamic de oră.
                """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sloturi disponibile returnate",
            content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "Dată în format invalid (așteptat: yyyy-MM-dd)",
            content = @Content)
    })
    public ResponseEntity<List<String>> slots(
            @Parameter(description = "ID-ul doctorului", example = "1", required = true)
            @PathVariable Long doctorId,
            @Parameter(description = "Data în format ISO (yyyy-MM-dd)", example = "2026-05-20", required = true)
            @PathVariable String date) {
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
        if (doctor == null) return ResponseEntity.ok(List.of());

        LocalDate localDate;
        try { localDate = LocalDate.parse(date); }
        catch (Exception e) { return ResponseEntity.badRequest().build(); }

        int dow = localDate.getDayOfWeek().getValue(); 
        Optional<DoctorSchedule> scheduleOpt = scheduleRepository.findByDoctorAndDayOfWeek(doctor, dow);

        int startH = 8, endH = 18;
        if (scheduleOpt.isPresent()) {
            DoctorSchedule s = scheduleOpt.get();
            if (!s.isAvailable()) return ResponseEntity.ok(List.of());
            startH = s.getStartHour();
            endH   = s.getEndHour();
        }
        LocalDateTime dayStart = localDate.atStartOfDay();
        LocalDateTime dayEnd   = localDate.plusDays(1).atStartOfDay();
        Set<Integer> bookedHours = new HashSet<>();
        for (Appointment a : appointmentRepository.findByDoctorAndStatus(doctor, "CONFIRMED")) {
            if (!a.getAppointmentDate().isBefore(dayStart) && a.getAppointmentDate().isBefore(dayEnd)) {
                bookedHours.add(a.getAppointmentDate().getHour());
            }
        }
        for (Appointment a : appointmentRepository.findByDoctorAndStatus(doctor, "PENDING")) {
            if (!a.getAppointmentDate().isBefore(dayStart) && a.getAppointmentDate().isBefore(dayEnd)) {
                bookedHours.add(a.getAppointmentDate().getHour());
            }
        }

        List<String> slots = new ArrayList<>();
        for (int h = startH; h < endH; h++) {
            if (!bookedHours.contains(h)) {
                slots.add(String.format("%02d:00", h));
            }
        }
        return ResponseEntity.ok(slots);
    }

    private Map<Integer, String> dayNames() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            m.put(i, DayOfWeek.of(i).getDisplayName(TextStyle.FULL, Locale.of("ro", "RO")));
        }
        return m;
    }

    private int parseHour(String val, int def) {
        try { return Integer.parseInt(val); } catch (Exception e) { return def; }
    }
}
