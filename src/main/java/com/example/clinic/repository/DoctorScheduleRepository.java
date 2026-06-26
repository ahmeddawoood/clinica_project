package com.example.clinic.repository;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByDoctorOrderByDayOfWeek(Doctor doctor);
    Optional<DoctorSchedule> findByDoctorAndDayOfWeek(Doctor doctor, int dayOfWeek);
}
