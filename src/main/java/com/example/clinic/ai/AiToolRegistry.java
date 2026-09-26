package com.example.clinic.ai;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiToolRegistry {

    private final MyAppointmentsTool myAppointmentsTool;
    private final MyPrescriptionsTool myPrescriptionsTool;
    private final MyMedicalHistoryTool myMedicalHistoryTool;
    private final DoctorInformationTool doctorInformationTool;

    public AiToolRegistry(MyAppointmentsTool myAppointmentsTool,
                          MyPrescriptionsTool myPrescriptionsTool,
                          MyMedicalHistoryTool myMedicalHistoryTool,
                          DoctorInformationTool doctorInformationTool) {
        this.myAppointmentsTool = myAppointmentsTool;
        this.myPrescriptionsTool = myPrescriptionsTool;
        this.myMedicalHistoryTool = myMedicalHistoryTool;
        this.doctorInformationTool = doctorInformationTool;
    }

    public List<Object> toolsForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Authentication is required");
        }

        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_PATIENT".equals(authority.getAuthority()))) {
            throw new IllegalStateException("AI assistant is not available for this user");
        }

        return List.of(
                myAppointmentsTool,
                myPrescriptionsTool,
                myMedicalHistoryTool,
                doctorInformationTool);
    }
}
