package com.example.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public class MedicalRecordForm {

    private Long patientId;

    @NotBlank(message = "Titlul este obligatoriu")
    private String title;

    private String symptoms;

    private String diagnosis;

    private String treatment;

    private String notes;

    public MedicalRecordForm() {}

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getTreatment() { return treatment; }
    public void setTreatment(String treatment) { this.treatment = treatment; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
