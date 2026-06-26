package com.example.clinic.repository;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppointmentRepositoryTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @PersistenceContext EntityManager em;
    @Autowired AppointmentRepository appointmentRepository;

    private Doctor doctor;
    private Patient patient;

    @BeforeEach
    void setUp() {
        User doctorUser = new User();
        doctorUser.setEmail("dr.repotest@clinic.com");
        doctorUser.setPassword("hashed");
        doctorUser.setRole("DOCTOR");
        em.persist(doctorUser);

        doctor = new Doctor();
        doctor.setFirstName("Elena");
        doctor.setLastName("Marin");
        doctor.setSpecialty("Cardiologie");
        doctor.setUser(doctorUser);
        em.persist(doctor);

        User patientUser = new User();
        patientUser.setEmail("patient.repotest@example.com");
        patientUser.setPassword("hashed");
        patientUser.setRole("PATIENT");
        em.persist(patientUser);

        patient = new Patient();
        patient.setFirstName("Ion");
        patient.setLastName("Popescu");
        patient.setUser(patientUser);
        em.persist(patient);

        em.flush();
    }

    
    private Appointment persist(String status, LocalDateTime date, LocalDateTime createdAt) {
        Appointment appt = new Appointment();
        appt.setDoctor(doctor);
        appt.setPatient(patient);
        appt.setStatus(status);
        appt.setAppointmentDate(date);
        if (createdAt != null) appt.setCreatedAt(createdAt);
        em.persist(appt);
        em.flush();
        return appt;
    }

    @Test
    void countConflicts_sameDocDateNotCancelled_returnsOne() {
        LocalDateTime slot = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        persist("CONFIRMED", slot, null);

        assertThat(appointmentRepository.countConflicts(doctor, slot)).isEqualTo(1);
    }

    @Test
    void countConflicts_cancelledAppointmentSameSlot_notCounted() {
        LocalDateTime slot = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0);
        persist("CANCELLED", slot, null);

        assertThat(appointmentRepository.countConflicts(doctor, slot)).isZero();
    }

    @Test
    void countConflicts_differentDoctorSameSlot_returnsZero() {
        User otherUser = new User();
        otherUser.setEmail("dr.other.repotest@clinic.com");
        otherUser.setPassword("hashed");
        otherUser.setRole("DOCTOR");
        em.persist(otherUser);

        Doctor otherDoctor = new Doctor();
        otherDoctor.setFirstName("Mihai");
        otherDoctor.setLastName("Stan");
        otherDoctor.setSpecialty("Neurologie");
        otherDoctor.setUser(otherUser);
        em.persist(otherDoctor);
        em.flush();

        LocalDateTime slot = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        Appointment appt = new Appointment();
        appt.setDoctor(otherDoctor);
        appt.setPatient(patient);
        appt.setStatus("CONFIRMED");
        appt.setAppointmentDate(slot);
        em.persist(appt);
        em.flush();

        assertThat(appointmentRepository.countConflicts(doctor, slot)).isZero();
    }

    @Test
    void findByStatusAndCreatedAtBefore_oldPendingPayment_returned() {
        persist("PENDING_PAYMENT", LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(2));

        List<Appointment> expired = appointmentRepository
                .findByStatusAndCreatedAtBefore("PENDING_PAYMENT", LocalDateTime.now().minusHours(1));

        assertThat(expired).hasSize(1);
    }

    @Test
    void findByStatusAndCreatedAtBefore_recentPendingPayment_notReturned() {
        persist("PENDING_PAYMENT", LocalDateTime.now().plusDays(1), LocalDateTime.now().minusMinutes(10));

        List<Appointment> expired = appointmentRepository
                .findByStatusAndCreatedAtBefore("PENDING_PAYMENT", LocalDateTime.now().minusHours(1));

        assertThat(expired).isEmpty();
    }

    @Test
    void findByStatusAndCreatedAtBefore_confirmedStatusOld_notReturned() {
        persist("CONFIRMED", LocalDateTime.now().plusDays(1), LocalDateTime.now().minusHours(3));

        List<Appointment> expired = appointmentRepository
                .findByStatusAndCreatedAtBefore("PENDING_PAYMENT", LocalDateTime.now().minusHours(1));

        assertThat(expired).isEmpty();
    }

    @Test
    void findByStatusAndAppointmentDateBetween_confirmedTomorrow_returned() {
        LocalDateTime tomorrow10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        persist("CONFIRMED", tomorrow10am, null);

        LocalDateTime windowStart = LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay();
        LocalDateTime windowEnd = windowStart.plusDays(1);
        List<Appointment> upcoming = appointmentRepository
                .findByStatusAndAppointmentDateBetween("CONFIRMED", windowStart, windowEnd);

        assertThat(upcoming).hasSize(1);
    }

    @Test
    void findByStatusAndAppointmentDateBetween_cancelledTomorrow_notReturned() {
        LocalDateTime tomorrow10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        persist("CANCELLED", tomorrow10am, null);

        LocalDateTime windowStart = LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay();
        LocalDateTime windowEnd = windowStart.plusDays(1);
        List<Appointment> upcoming = appointmentRepository
                .findByStatusAndAppointmentDateBetween("CONFIRMED", windowStart, windowEnd);

        assertThat(upcoming).isEmpty();
    }

    @Test
    void findByStripePaymentIntentId_existingId_found() {
        Appointment appt = persist("PENDING_PAYMENT", LocalDateTime.now().plusDays(1), null);
        appt.setStripePaymentIntentId("pi_test_abc123");
        em.flush();

        Optional<Appointment> found = appointmentRepository.findByStripePaymentIntentId("pi_test_abc123");

        assertThat(found).isPresent();
        assertThat(found.get().getStripePaymentIntentId()).isEqualTo("pi_test_abc123");
    }

    @Test
    void findByStripePaymentIntentId_unknownId_empty() {
        assertThat(appointmentRepository.findByStripePaymentIntentId("pi_nonexistent")).isEmpty();
    }

    @Test
    void findByPatientOrderByAppointmentDateDesc_returnedNewestFirst() {
        persist("CONFIRMED", LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0), null);
        persist("PENDING",   LocalDateTime.now().plusDays(3).withHour(9).withMinute(0).withSecond(0).withNano(0), null);

        List<Appointment> results = appointmentRepository.findByPatientOrderByAppointmentDateDesc(patient);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAppointmentDate())
                .isAfterOrEqualTo(results.get(1).getAppointmentDate());
    }
}
