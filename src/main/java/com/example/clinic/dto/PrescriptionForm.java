package com.example.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public class PrescriptionForm {

    private Long patientId;
    private Long appointmentId;

    private String diagnosis;

    @NotBlank(message = "Medicamentele sunt obligatorii")
    private String medications;

    private String instructions;

    public PrescriptionForm() {}

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getMedications() { return medications; }
    public void setMedications(String medications) { this.medications = medications; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
}
