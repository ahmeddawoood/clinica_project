package com.example.clinic.ai;

import com.example.clinic.domain.Appointment;
import com.example.clinic.service.PatientService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyAppointmentsTool {

    private final PatientService patientService;

    public MyAppointmentsTool(PatientService patientService) {
        this.patientService = patientService;
    }

    @Tool(description = "Get the authenticated patient's own appointments. Never accepts a patient ID or another user's identity.")
    public List<MyAppointment> getMyAppointments() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Authentication is required");
        }

        return patientService.getAppointments(authentication.getName()).stream()
                .map(MyAppointment::from)
                .toList();
    }

    public record MyAppointment(
            Long id,
            String status,
            String appointmentDate,
            String priority,
            String doctorName,
            String doctorSpecialty) {

        static MyAppointment from(Appointment appointment) {
            return new MyAppointment(
                    appointment.getId(),
                    appointment.getStatus(),
                    appointment.getAppointmentDate() == null ? null : appointment.getAppointmentDate().toString(),
                    appointment.getPriority(),
                    appointment.getDoctor() == null ? null : appointment.getDoctor().getFullName(),
                    appointment.getDoctor() == null ? null : appointment.getDoctor().getSpecialty());
        }
    }
}
