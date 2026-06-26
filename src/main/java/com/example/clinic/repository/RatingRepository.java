package com.example.clinic.repository;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByAppointment(Appointment appointment);
    List<Rating> findByDoctorOrderByCreatedAtDesc(Doctor doctor);

    @Query("SELECT COALESCE(AVG(r.stars), 0) FROM Rating r WHERE r.doctor = :doctor")
    Double averageByDoctor(@Param("doctor") Doctor doctor);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.doctor = :doctor")
    long countByDoctor(@Param("doctor") Doctor doctor);
}
