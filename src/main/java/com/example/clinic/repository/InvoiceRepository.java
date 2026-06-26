package com.example.clinic.repository;

import com.example.clinic.domain.Invoice;
import com.example.clinic.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("SELECT i FROM Invoice i JOIN FETCH i.patient p JOIN FETCH p.user WHERE i.id = :id")
    Optional<Invoice> findByIdWithPatient(@Param("id") Long id);
    List<Invoice> findByPatientOrderByIssuedAtDesc(Patient patient);
    List<Invoice> findByStatusOrderByIssuedAtDesc(String status);
    List<Invoice> findByAppointmentOrderByIssuedAtDesc(com.example.clinic.domain.Appointment appointment);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status = 'PAID'")
    BigDecimal sumPaidAmount();

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.status = 'PENDING'")
    BigDecimal sumPendingAmount();

    long countByStatus(String status);
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i " +
           "WHERE i.status = 'PAID' AND i.paidAt >= :start AND i.paidAt < :end")
    BigDecimal sumPaidAmountInPeriod(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}
