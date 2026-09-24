package com.example.clinic.service;

import com.example.clinic.domain.Prescription;
import com.example.clinic.dto.PrescriptionForm;

import java.util.List;

public interface PrescriptionService {
    List<Prescription> getPrescriptionsByPatientEmail(String email);
    List<Prescription> getPrescriptionsByDoctorEmail(String email);
    Prescription getPrescriptionById(Long id);
    void writePrescription(PrescriptionForm form, String doctorEmail);
    byte[] generatePdf(Long prescriptionId);
    byte[] generatePatientPdf(Long prescriptionId, String patientEmail);
    byte[] generateDoctorPdf(Long prescriptionId, String doctorEmail);
}
