package com.example.clinic.repository;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    @Query("SELECT r FROM Prescription r JOIN FETCH r.doctor JOIN FETCH r.patient WHERE r.patient = :patient ORDER BY r.issuedAt DESC")
    List<Prescription> findByPatientOrderByIssuedAtDesc(@Param("patient") Patient patient);

    @Query("SELECT r FROM Prescription r JOIN FETCH r.doctor JOIN FETCH r.patient WHERE r.doctor = :doctor ORDER BY r.issuedAt DESC")
    List<Prescription> findByDoctorOrderByIssuedAtDesc(@Param("doctor") Doctor doctor);

    @Query("SELECT r FROM Prescription r JOIN FETCH r.doctor JOIN FETCH r.patient WHERE r.id = :id")
    Optional<Prescription> findByIdWithRelations(@Param("id") Long id);
}
