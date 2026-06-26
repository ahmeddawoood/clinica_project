package com.example.clinic.repository;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.MedicalReport;
import com.example.clinic.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicalReportRepository extends JpaRepository<MedicalReport, Long> {
    List<MedicalReport> findByAppointmentOrderByGeneratedAtDesc(Appointment appointment);
    Optional<MedicalReport> findTopByAppointmentOrderByGeneratedAtDesc(Appointment appointment);

    @Query("SELECT r FROM MedicalReport r JOIN FETCH r.doctor JOIN FETCH r.patient WHERE r.patient = :patient ORDER BY r.generatedAt DESC")
    List<MedicalReport> findByPatientOrderByGeneratedAtDesc(@Param("patient") Patient patient);
}
