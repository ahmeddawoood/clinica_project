package com.example.clinic.integration;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DuplicateBookingException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.StripeService;
import com.example.clinic.service.DoctorService;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingFlowIntegrationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired PatientService patientService;
    @Autowired DoctorService doctorService;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PATIENT_EMAIL = "integration.patient@test.com";
    private static final String DOCTOR_EMAIL  = "integration.doctor@test.com";

    private Doctor doctor;

    @BeforeEach
    void seedDatabase() {
        User patientUser = new User();
        patientUser.setEmail(PATIENT_EMAIL);
        patientUser.setPassword(passwordEncoder.encode("password"));
        patientUser.setRole("PATIENT");
        userRepository.save(patientUser);

        Patient patient = new Patient();
        patient.setFirstName("Ana");
        patient.setLastName("Ionescu");
        patient.setUser(patientUser);
        patientRepository.save(patient);

        User doctorUser = new User();
        doctorUser.setEmail(DOCTOR_EMAIL);
        doctorUser.setPassword(passwordEncoder.encode("password"));
        doctorUser.setRole("DOCTOR");
        userRepository.save(doctorUser);

        doctor = new Doctor();
        doctor.setFirstName("Mihai");
        doctor.setLastName("Marin");
        doctor.setSpecialty("Cardiologie");
        doctor.setUser(doctorUser);
        doctorRepository.save(doctor);
    }

    private AppointmentForm validForm(LocalDateTime dateTime) {
        AppointmentForm form = new AppointmentForm();
        form.setDoctorId(doctor.getId());
        form.setAppointmentDate(dateTime);
        form.setNotes("Routine check");
        return form;
    }

    @Test
    void fullBookingLifecycle_happyPath() {
        LocalDateTime slot = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);

        Long appointmentId = patientService.bookAppointment(validForm(slot), PATIENT_EMAIL);
        assertThat(appointmentId).isNotNull();

        Appointment appt = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(appt.getPatient().getUser().getEmail()).isEqualTo(PATIENT_EMAIL);
        assertThat(appt.getDoctor().getSpecialty()).isEqualTo("Cardiologie");
        appt.setBookingFeePaid(true);
        appt.setStatus("PENDING");
        appointmentRepository.save(appt);

        doctorService.confirmAppointment(appointmentId);
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo("CONFIRMED");

        doctorService.completeAppointment(appointmentId, "Follow-up in 2 weeks");
        Appointment completed = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getNotes()).isEqualTo("Follow-up in 2 weeks");
    }

    @Test
    void fullBookingLifecycle_cancelledByPatient() {
        LocalDateTime slot = LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);

        Long appointmentId = patientService.bookAppointment(validForm(slot), PATIENT_EMAIL);

        patientService.cancelAppointment(appointmentId, "Nu mai pot ajunge");

        Appointment cancelled = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelReason()).isEqualTo("Nu mai pot ajunge");
    }

    @Test
    void fullBookingLifecycle_cancelledByDoctor() {
        LocalDateTime slot = LocalDateTime.now().plusDays(4).withHour(9).withMinute(0).withSecond(0).withNano(0);

        Long appointmentId = patientService.bookAppointment(validForm(slot), PATIENT_EMAIL);
        Appointment appt = appointmentRepository.findById(appointmentId).orElseThrow();
        appt.setStatus("PENDING");
        appt.setBookingFeePaid(true);
        appointmentRepository.save(appt);

        doctorService.cancelAppointment(appointmentId);

        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo("CANCELLED");
    }

    @Test
    void bookAppointment_duplicateSlot_throwsDuplicateBookingException() {
        LocalDateTime slot = LocalDateTime.now().plusDays(5).withHour(11).withMinute(0).withSecond(0).withNano(0);
        patientService.bookAppointment(validForm(slot), PATIENT_EMAIL);
        assertThatThrownBy(() -> patientService.bookAppointment(validForm(slot), PATIENT_EMAIL))
                .isInstanceOf(DuplicateBookingException.class);
    }

    @Test
    void bookAppointment_pastDate_throwsClinicException() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);

        assertThatThrownBy(() -> patientService.bookAppointment(validForm(past), PATIENT_EMAIL))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("viitor");
    }

    @Test
    void bookAppointment_beforeBusinessHours_throwsClinicException() {
        LocalDateTime earlySlot = LocalDateTime.now().plusDays(1).withHour(6).withMinute(0).withSecond(0).withNano(0);

        assertThatThrownBy(() -> patientService.bookAppointment(validForm(earlySlot), PATIENT_EMAIL))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("08:00");
    }

    @Test
    void confirmAppointment_withoutPayment_throwsClinicException() {
        LocalDateTime slot = LocalDateTime.now().plusDays(2).withHour(15).withMinute(0).withSecond(0).withNano(0);
        Long id = patientService.bookAppointment(validForm(slot), PATIENT_EMAIL);

        Appointment appt = appointmentRepository.findById(id).orElseThrow();
        appt.setStatus("PENDING");
        appt.setBookingFeePaid(false);
        appointmentRepository.save(appt);

        assertThatThrownBy(() -> doctorService.confirmAppointment(id))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("taxa de rezervare");
    }

    @Test
    void completeAppointment_notConfirmed_throwsClinicException() {
        LocalDateTime slot = LocalDateTime.now().plusDays(6).withHour(10).withMinute(0).withSecond(0).withNano(0);
        Long id = patientService.bookAppointment(validForm(slot), PATIENT_EMAIL);

        Appointment appt = appointmentRepository.findById(id).orElseThrow();
        appt.setStatus("PENDING");
        appointmentRepository.save(appt);

        assertThatThrownBy(() -> doctorService.completeAppointment(id, "notes"))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("confirmate");
    }

    @Test
    void bookAppointment_urgentSymptoms_savedWithHighPriority() {
        LocalDateTime slot = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
        AppointmentForm form = validForm(slot);
        form.setNotes("chest pain and shortness of breath");

        Long id = patientService.bookAppointment(form, PATIENT_EMAIL);

        Appointment appt = appointmentRepository.findById(id).orElseThrow();
        assertThat(appt.getPriority()).isIn("CRITICAL", "HIGH");
    }

    @Test
    void bookAppointment_routineSymptoms_savedWithLowPriority() {
        LocalDateTime slot = LocalDateTime.now().plusDays(3).withHour(16).withMinute(0).withSecond(0).withNano(0);
        AppointmentForm form = validForm(slot);
        form.setNotes("annual checkup");

        Long id = patientService.bookAppointment(form, PATIENT_EMAIL);

        assertThat(appointmentRepository.findById(id).orElseThrow().getPriority()).isEqualTo("LOW");
    }
}
