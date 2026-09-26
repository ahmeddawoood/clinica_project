package com.example.clinic.ai;

import com.example.clinic.domain.Appointment;
import com.example.clinic.service.PatientService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClinicAiTool {

    private final PatientService patientService;

    public ClinicAiTool(PatientService patientService) {
        this.patientService = patientService;
    }

    public List<Appointment> getMyAppointments() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Authentication is required to access appointments");
        }

        return patientService.getAppointments(authentication.getName());
    }
}
