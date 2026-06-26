package com.example.clinic.repository;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatient(Patient patient);
    List<Appointment> findByDoctor(Doctor doctor);
    List<Appointment> findByPatientOrderByAppointmentDateDesc(Patient patient);
    List<Appointment> findByDoctorOrderByAppointmentDateDesc(Doctor doctor);
    List<Appointment> findByStatus(String status);
    List<Appointment> findByDoctorAndStatus(Doctor doctor, String status);
    long countByStatus(String status);
    List<Appointment> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
    List<Appointment> findByStatusAndAppointmentDateBetween(String status, LocalDateTime start, LocalDateTime end);
    java.util.Optional<Appointment> findByStripePaymentIntentId(String stripePaymentIntentId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor = :doctor " +
           "AND a.appointmentDate = :date AND a.status != 'CANCELLED'")
    long countConflicts(@Param("doctor") Doctor doctor, @Param("date") LocalDateTime date);

    @Query("SELECT a FROM Appointment a ORDER BY a.appointmentDate DESC")
    List<Appointment> findAllOrderByDateDesc();

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.appointmentDate >= :start AND a.appointmentDate < :end")
    long countInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.user.email = :email AND a.status = 'PENDING'")
    long countPendingByDoctorEmail(@Param("email") String email);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.user.email = :email AND a.status = 'PENDING'")
    long countPendingByPatientEmail(@Param("email") String email);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.patient.user.email = :email AND a.status = 'PENDING_PAYMENT'")
    long countPendingPaymentByPatientEmail(@Param("email") String email);
    @Query("SELECT a FROM Appointment a WHERE a.doctor = :doctor " +
           "AND a.appointmentDate >= :start AND a.appointmentDate < :end " +
           "AND a.status NOT IN ('CANCELLED', 'PENDING_PAYMENT') " +
           "ORDER BY a.appointmentDate ASC")
    List<Appointment> findTodayByDoctor(@Param("doctor") Doctor doctor,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    @Query("SELECT a FROM Appointment a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:name IS NULL OR LOWER(a.patient.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "OR LOWER(a.patient.lastName) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "ORDER BY a.appointmentDate DESC")
    List<Appointment> searchAppointments(@Param("status") String status, @Param("name") String name);
    @Query("SELECT HOUR(a.appointmentDate), COUNT(a) FROM Appointment a " +
           "WHERE a.status != 'CANCELLED' " +
           "GROUP BY HOUR(a.appointmentDate) ORDER BY HOUR(a.appointmentDate)")
    List<Object[]> countByHourOfDay();
    @Query("SELECT COUNT(a) FROM Appointment a " +
           "WHERE a.appointmentDate >= :start AND a.appointmentDate < :end AND a.status != 'CANCELLED'")
    long countNonCancelledInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
