package com.example.clinic.repository;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Doctor d WHERE d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);
    List<Doctor> findBySpecialtyIgnoreCase(String specialty);

    @Query("SELECT DISTINCT d.specialty FROM Doctor d WHERE d.specialty IS NOT NULL ORDER BY d.specialty")
    List<String> findAllSpecialties();

    @Query("SELECT d FROM Doctor d WHERE d.specialty = :specialty ORDER BY d.lastName, d.firstName")
    List<Doctor> findBySpecialty(@Param("specialty") String specialty);
}
