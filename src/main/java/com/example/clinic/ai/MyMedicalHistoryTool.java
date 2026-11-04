package com.example.clinic.ai;

import com.example.clinic.domain.MedicalRecord;
import com.example.clinic.service.MedicalRecordService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyMedicalHistoryTool {

    private final MedicalRecordService medicalRecordService;

    public MyMedicalHistoryTool(MedicalRecordService medicalRecordService) {
        this.medicalRecordService = medicalRecordService;
    }

    @Tool(description = "Get the authenticated patient's own medical history. Never accepts a patient ID or another user's identity.")
    public List<MyMedicalRecord> getMyMedicalHistory() {
        String email = currentPatientEmail();
        return medicalRecordService.getRecordsByPatientEmail(email).stream()
                .map(MyMedicalRecord::from)
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

    public record MyMedicalRecord(
            Long id,
            String title,
            String symptoms,
            String diagnosis,
            String treatment,
            String notes,
            String createdAt,
            String updatedAt,
            String doctorName,
            String doctorSpecialty) {

        static MyMedicalRecord from(MedicalRecord record) {
            return new MyMedicalRecord(
                    record.getId(),
                    record.getTitle(),
                    record.getSymptoms(),
                    record.getDiagnosis(),
                    record.getTreatment(),
                    record.getNotes(),
                    record.getCreatedAt() == null ? null : record.getCreatedAt().toString(),
                    record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString(),
                    record.getDoctor() == null ? null : record.getDoctor().getFullName(),
                    record.getDoctor() == null ? null : record.getDoctor().getSpecialty());
        }
    }
}
