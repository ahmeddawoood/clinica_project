package com.example.clinic.ai;

import com.example.clinic.domain.Prescription;
import com.example.clinic.service.PrescriptionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyPrescriptionsTool {

    private final PrescriptionService prescriptionService;

    public MyPrescriptionsTool(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @Tool(description = "Get the authenticated patient's own prescriptions. Never accepts a patient ID or another user's identity.")
    public List<MyPrescription> getMyPrescriptions() {
        String email = currentPatientEmail();
        return prescriptionService.getPrescriptionsByPatientEmail(email).stream()
                .map(MyPrescription::from)
                .toList();
    }

    private String currentPatientEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Authentication is required");
        }
        return authentication.getName();
    }

    public record MyPrescription(
            Long id,
            String issuedAt,
            String doctorName,
            String doctorSpecialty,
            String medications,
            String instructions,
            String diagnosis) {

        static MyPrescription from(Prescription prescription) {
            return new MyPrescription(
                    prescription.getId(),
                    prescription.getIssuedAt() == null ? null : prescription.getIssuedAt().toString(),
                    prescription.getDoctor() == null ? null : prescription.getDoctor().getFullName(),
                    prescription.getDoctor() == null ? null : prescription.getDoctor().getSpecialty(),
                    prescription.getMedications(),
                    prescription.getInstructions(),
                    prescription.getDiagnosis());
        }
    }
}
