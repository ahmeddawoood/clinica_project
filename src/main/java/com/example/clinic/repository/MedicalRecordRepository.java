package com.example.clinic.repository;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.MedicalRecord;
import com.example.clinic.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    @Query("SELECT r FROM MedicalRecord r JOIN FETCH r.doctor JOIN FETCH r.patient WHERE r.patient = :patient ORDER BY r.createdAt DESC")
    List<MedicalRecord> findByPatientOrderByCreatedAtDesc(@Param("patient") Patient patient);

    List<MedicalRecord> findByDoctorOrderByCreatedAtDesc(Doctor doctor);
    long countByPatient(Patient patient);
}
