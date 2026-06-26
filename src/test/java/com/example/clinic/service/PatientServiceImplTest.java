package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.dto.SymptomAnalysisResult;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DuplicateBookingException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientServiceImplTest {

    @Mock PatientRepository patientRepository;
    @Mock UserRepository userRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock AuditLogService auditLogService;
    @Mock NotificationService notificationService;
    @Mock ActivityLogService activityLogService;
    @Mock AiService aiService;
    @Mock StripeService stripeService;
    @Mock WebSocketNotificationService wsNotifications;
    @Mock MetricsService metricsService;

    @InjectMocks PatientServiceImpl service;

    private static final String PATIENT_EMAIL = "patient@example.com";
    private static final Long DOCTOR_ID = 10L;

    private User patientUser;
    private Patient patient;
    private Doctor doctor;
    private SymptomAnalysisResult lowPriorityAnalysis;

    @BeforeEach
    void setUp() {
        patientUser = new User();
        patientUser.setEmail(PATIENT_EMAIL);
        patientUser.setRole("PATIENT");

        patient = new Patient();
        patient.setFirstName("Maria");
        patient.setLastName("Popescu");
        patient.setUser(patientUser);

        User doctorUser = new User();
        doctorUser.setEmail("dr.smith@clinic.com");
        doctorUser.setRole("DOCTOR");

        doctor = new Doctor();
        doctor.setFirstName("John");
        doctor.setLastName("Smith");
        doctor.setSpecialty("Cardiologie");
        doctor.setUser(doctorUser);

        lowPriorityAnalysis = new SymptomAnalysisResult("LOW", false, "General", null);
    }

    private AppointmentForm validForm(LocalDateTime dateTime) {
        AppointmentForm form = new AppointmentForm();
        form.setDoctorId(DOCTOR_ID);
        form.setAppointmentDate(dateTime);
        form.setNotes("Headache");
        return form;
    }

    private void stubPatientLookup() {
        when(userRepository.findByEmail(PATIENT_EMAIL)).thenReturn(Optional.of(patientUser));
        when(patientRepository.findByUser(patientUser)).thenReturn(Optional.of(patient));
    }

    private void stubDoctorLookup() {
        when(doctorRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
    }

    private void stubNoConflicts() {
        when(appointmentRepository.countConflicts(any(), any())).thenReturn(0L);
    }

    private void stubAiAnalysis() {
        when(aiService.analyzeSymptoms(any())).thenReturn(lowPriorityAnalysis);
    }

    @Test
    void bookAppointment_happyPath_savesAndReturnsId() {
        stubPatientLookup();
        stubDoctorLookup();
        stubNoConflicts();
        stubAiAnalysis();

        Appointment saved = new Appointment();
        saved.setStatus("PENDING_PAYMENT");
        when(appointmentRepository.save(any())).thenReturn(saved);

        LocalDateTime future10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        service.bookAppointment(validForm(future10am), PATIENT_EMAIL);

        verify(appointmentRepository).save(argThat(a ->
                "PENDING_PAYMENT".equals(a.getStatus())
                && a.getPatient() == patient
                && a.getDoctor() == doctor
        ));
        verify(notificationService).notifyBookingCreated(any());
        verify(activityLogService).saveLog(any(), eq("BOOKING_CREATED"), anyString());
    }

    @Test
    void bookAppointment_pastDate_throwsClinicException() {
        stubPatientLookup();
        stubDoctorLookup();

        AppointmentForm form = validForm(LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> service.bookAppointment(form, PATIENT_EMAIL))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("viitor");
    }

    @Test
    void bookAppointment_nullDate_throwsClinicException() {
        stubPatientLookup();
        stubDoctorLookup();

        AppointmentForm form = validForm(null);

        assertThatThrownBy(() -> service.bookAppointment(form, PATIENT_EMAIL))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("viitor");
    }

    @Test
    void bookAppointment_beforeBusinessHours_throwsClinicException() {
        stubPatientLookup();
        stubDoctorLookup();

        LocalDateTime earlyMorning = LocalDateTime.now().plusDays(1).withHour(7).withMinute(30);
        AppointmentForm form = validForm(earlyMorning);

        assertThatThrownBy(() -> service.bookAppointment(form, PATIENT_EMAIL))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("08:00");
    }

    @Test
    void bookAppointment_afterBusinessHours_throwsClinicException() {
        stubPatientLookup();
        stubDoctorLookup();

        LocalDateTime evening = LocalDateTime.now().plusDays(1).withHour(18).withMinute(0);
        AppointmentForm form = validForm(evening);

        assertThatThrownBy(() -> service.bookAppointment(form, PATIENT_EMAIL))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("08:00");
    }

    @Test
    void bookAppointment_conflictExists_throwsDuplicateBookingException() {
        stubPatientLookup();
        stubDoctorLookup();
        when(appointmentRepository.countConflicts(any(), any())).thenReturn(1L);

        LocalDateTime future10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);

        assertThatThrownBy(() -> service.bookAppointment(validForm(future10am), PATIENT_EMAIL))
                .isInstanceOf(DuplicateBookingException.class);
    }

    @Test
    void bookAppointment_urgentSymptoms_logsWarningButStillSaves() {
        stubPatientLookup();
        stubDoctorLookup();
        stubNoConflicts();

        SymptomAnalysisResult urgentAnalysis =
                new SymptomAnalysisResult("CRITICAL", true, "Urgenta", "Chest pain");
        when(aiService.analyzeSymptoms(any())).thenReturn(urgentAnalysis);

        Appointment saved = new Appointment();
        saved.setStatus("PENDING_PAYMENT");
        when(appointmentRepository.save(any())).thenReturn(saved);

        LocalDateTime future10am = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        service.bookAppointment(validForm(future10am), PATIENT_EMAIL);

        verify(appointmentRepository).save(argThat(a -> "CRITICAL".equals(a.getPriority())));
    }

    @Test
    void cancelAppointment_happyPath_setsStatusAndNotifies() {
        Appointment appt = pendingPaidAppt();
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        service.cancelAppointment(5L, "Conflict de orar");

        assertThat(appt.getStatus()).isEqualTo("CANCELLED");
        assertThat(appt.getCancelReason()).isEqualTo("Conflict de orar");
        verify(notificationService).notifyAppointmentCancelled(eq(appt), eq("Conflict de orar"));
    }

    @Test
    void cancelAppointment_alreadyCancelled_isIdempotent() {
        Appointment appt = new Appointment();
        appt.setStatus("CANCELLED");
        when(appointmentRepository.findById(6L)).thenReturn(Optional.of(appt));

        service.cancelAppointment(6L, null);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void cancelAppointment_completed_throwsClinicException() {
        Appointment appt = new Appointment();
        appt.setStatus("COMPLETED");
        when(appointmentRepository.findById(7L)).thenReturn(Optional.of(appt));

        assertThatThrownBy(() -> service.cancelAppointment(7L, null))
                .isInstanceOf(ClinicException.class)
                .hasMessageContaining("finalizată");
    }

    @Test
    void cancelAppointment_notFound_throwsAppointmentNotFoundException() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelAppointment(99L, null))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void cancelAppointment_paidAndNotRefunded_triggersStripeRefund() throws StripeException {
        Appointment appt = pendingPaidAppt();
        appt.setStripePaymentIntentId("pi_test_123");
        appt.setBookingFeeRefunded(false);
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);
        when(stripeService.isEnabled()).thenReturn(true);

        service.cancelAppointment(5L, null);

        verify(stripeService).refund("pi_test_123");
    }

    @Test
    void cancelAppointment_unpaid_doesNotTriggerStripeRefund() throws StripeException {
        Appointment appt = new Appointment();
        appt.setStatus("PENDING_PAYMENT");
        appt.setBookingFeePaid(false);
        appt.setDoctor(doctor);
        appt.setPatient(patient);
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        service.cancelAppointment(5L, null);

        verifyNoInteractions(stripeService);
    }

    @Test
    void cancelAppointment_alreadyRefunded_doesNotTriggerStripeRefundAgain() throws StripeException {
        Appointment appt = pendingPaidAppt();
        appt.setStripePaymentIntentId("pi_test_123");
        appt.setBookingFeeRefunded(true);
        when(appointmentRepository.findById(5L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any())).thenReturn(appt);

        service.cancelAppointment(5L, null);

        verifyNoInteractions(stripeService);
    }

    private Appointment pendingPaidAppt() {
        Appointment appt = new Appointment();
        appt.setStatus("PENDING");
        appt.setBookingFeePaid(true);
        appt.setDoctor(doctor);
        appt.setPatient(patient);
        appt.setAppointmentDate(LocalDateTime.now().plusDays(1));
        return appt;
    }
}
