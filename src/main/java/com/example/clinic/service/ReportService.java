package com.example.clinic.service;

import com.example.clinic.domain.MedicalReport;

import java.util.List;

public interface ReportService {
    MedicalReport generate(Long appointmentId, String diagnosis, String treatment, String notes);
    MedicalReport findById(Long reportId);
    List<MedicalReport> findByAppointment(Long appointmentId);
}
