package com.example.clinic.controller;

import com.example.clinic.domain.Patient;
import com.example.clinic.dto.PrescriptionForm;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.DoctorService;
import com.example.clinic.service.PatientService;
import com.example.clinic.service.PrescriptionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PatientService patientService;
    private final DoctorService doctorService;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public PrescriptionController(PrescriptionService prescriptionService,
                                   PatientService patientService,
                                   DoctorService doctorService,
                                   PatientRepository patientRepository,
                                   AppointmentRepository appointmentRepository,
                                   UserRepository userRepository) {
        this.prescriptionService = prescriptionService;
        this.patientService = patientService;
        this.doctorService = doctorService;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/patient/prescriptions")
    public String patientList(Model model, Principal principal) {
        Patient patient = patientService.getPatientByEmail(principal.getName());
        model.addAttribute("patient", patient);
        model.addAttribute("prescriptions",
                prescriptionService.getPrescriptionsByPatientEmail(principal.getName()));
        return "patient/prescriptions";
    }

    @GetMapping("/patient/prescriptions/{id}/download")
    public ResponseEntity<byte[]> patientDownload(@PathVariable Long id, Principal principal) {
        byte[] pdf = prescriptionService.generatePatientPdf(id, principal.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reteta-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/doctor/prescriptions")
    public String doctorList(Model model, Principal principal) {
        model.addAttribute("doctor", doctorService.getDoctorByEmail(principal.getName()));
        model.addAttribute("prescriptions",
                prescriptionService.getPrescriptionsByDoctorEmail(principal.getName()));
        model.addAttribute("patients", patientRepository.findAll());
        model.addAttribute("appointments",
                appointmentRepository.findByDoctorOrderByAppointmentDateDesc(
                        doctorService.getDoctorByEmail(principal.getName())));
        model.addAttribute("form", new PrescriptionForm());
        return "doctor/prescriptions";
    }

    @PostMapping("/doctor/prescriptions/save")
    public String doctorSave(@ModelAttribute PrescriptionForm form,
                             Principal principal, RedirectAttributes ra) {
        if (form.getMedications() == null || form.getMedications().isBlank()) {
            ra.addFlashAttribute("error", "Medicamentele sunt obligatorii.");
            return "redirect:/doctor/prescriptions";
        }
        prescriptionService.writePrescription(form, principal.getName());
        ra.addFlashAttribute("success", "Rețeta a fost salvată cu succes.");
        return "redirect:/doctor/prescriptions";
    }

    @GetMapping("/doctor/prescriptions/{id}/download")
    public ResponseEntity<byte[]> doctorDownload(@PathVariable Long id, Principal principal) {
        byte[] pdf = prescriptionService.generateDoctorPdf(id, principal.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reteta-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
