package com.example.clinic.ai;

import com.example.clinic.domain.Doctor;
import com.example.clinic.service.DoctorService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DoctorInformationTool {

    private final DoctorService doctorService;

    public DoctorInformationTool(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @Tool(description = "Find doctors by specialty. Use this only to provide doctor information available in MediClinic. Never use it to access patient records or other private clinical data.")
    public List<DoctorSummary> findDoctorsBySpecialty(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return List.of();
        }

        String normalized = specialty.trim().toLowerCase();
        return doctorService.getAllDoctors().stream()
                .filter(doctor -> doctor.getSpecialty() != null
                        && doctor.getSpecialty().toLowerCase().contains(normalized))
                .map(DoctorSummary::from)
                .toList();
    }

    public record DoctorSummary(
            Long id,
            String fullName,
            String specialty) {

        static DoctorSummary from(Doctor doctor) {
            return new DoctorSummary(
                    doctor.getId(),
                    doctor.getFullName(),
                    doctor.getSpecialty());
        }
    }
}
