package com.example.clinic.service;

import com.example.clinic.domain.MedicalRecord;
import com.example.clinic.dto.MedicalRecordForm;

import java.util.List;

public interface MedicalRecordService {
    List<MedicalRecord> getRecordsByPatientEmail(String email);
    List<MedicalRecord> getRecordsByDoctorEmail(String email);
    MedicalRecord getRecordById(Long id);
    void addRecord(MedicalRecordForm form, String doctorEmail);
    void deleteRecord(Long id);
}
