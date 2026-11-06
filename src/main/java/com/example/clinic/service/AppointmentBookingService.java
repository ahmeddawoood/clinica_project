package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.dto.ProfileForm;
import com.example.clinic.dto.SymptomAnalysisResult;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DuplicateBookingException;
import com.example.clinic.exception.PatientNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentBookingService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentBookingService.class);

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final AiService aiService;
    private final StripeService stripeService;
    private final WebSocketNotificationService wsNotifications;
    private final MetricsService metricsService;
    private final AppointmentAvailabilityService availability;

    public AppointmentBookingService(PatientRepository patientRepository,
                               UserRepository userRepository,
                               AppointmentRepository appointmentRepository,
                               DoctorRepository doctorRepository,
                               AuditLogService auditLogService,
                               NotificationService notificationService,
                               ActivityLogService activityLogService,
                               AiService aiService,
                               StripeService stripeService,
                               WebSocketNotificationService wsNotifications,
                               MetricsService metricsService, AppointmentAvailabilityService availability) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.aiService = aiService;
        this.stripeService = stripeService;
        this.wsNotifications = wsNotifications;
        this.metricsService = metricsService;
        this.availability = availability;
    }

    private Patient getPatientByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new PatientNotFoundException(email));
        return patientRepository.findByUser(user)
                .orElseThrow(() -> new PatientNotFoundException(email));
    }

    @Transactional
    public Long bookAppointment(AppointmentForm form, String email) {
        Patient patient = getPatientByEmail(email);
        Doctor doctor = doctorRepository.findByIdForUpdate(form.getDoctorId())
                .orElseThrow(() -> new com.example.clinic.exception.DoctorNotFoundException(
                        "id=" + form.getDoctorId()));

        availability.requireBookableTime(doctor, form.getAppointmentDate());
        long conflicts = appointmentRepository.countConflicts(doctor, form.getAppointmentDate());
        if (conflicts > 0) {
            throw new DuplicateBookingException(doctor.getFullName(),
                    form.getAppointmentDate().toString());
        }

        SymptomAnalysisResult analysis = aiService.analyzeSymptoms(form.getNotes());
        if (analysis.isUrgent()) {
            log.warn("Booking flagged for review: priority={}", analysis.getPriority());
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(form.getAppointmentDate());
        appointment.setNotes(form.getNotes());
        appointment.setStatus("PENDING_PAYMENT");
        appointment.setPriority(analysis.getPriority());

        Appointment saved = appointmentRepository.save(appointment);

        auditLogService.log(email, "BOOKING_CREATED",
                "Appointment #" + saved.getId() + " created | Dr. " + doctor.getFullName()
                + " | " + form.getAppointmentDate() + " | Priority: " + saved.getPriority());
        log.info("Appointment created (awaiting payment): id={} patient={} doctor={} priority={}",
                saved.getId(), email, doctor.getFullName(), saved.getPriority());

        notificationService.notifyBookingCreated(saved);
        wsNotifications.notifyDoctor(saved, "NEW_BOOKING",
                patient.getFullName() + " a programat o consultație pe " + form.getAppointmentDate().toLocalDate());
        activityLogService.saveLog(saved.getId(), "BOOKING_CREATED",
                "Appointment created by " + patient.getFullName()
                + " with Dr. " + doctor.getFullName() + " - " + doctor.getSpecialty());
        metricsService.bookingCreated();

        return saved.getId();
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if ("CANCELLED".equals(appointment.getStatus())) return;
        if ("COMPLETED".equals(appointment.getStatus())) {
            throw new ClinicException("O programare finalizată nu poate fi anulată.");
        }

        appointment.setStatus("CANCELLED");
        if (reason != null && !reason.isBlank()) appointment.setCancelReason(reason);
        appointmentRepository.save(appointment);
        if (appointment.isBookingFeePaid()
                && appointment.getStripePaymentIntentId() != null
                && !appointment.isBookingFeeRefunded()
                && stripeService.isEnabled()
                && !appointment.getStripePaymentIntentId().startsWith("local_demo_")) {
            try {
                stripeService.refund(appointment.getStripePaymentIntentId());
                metricsService.refundIssued();
                log.info("Refund triggered for cancelled appointment #{}", appointmentId);
            } catch (Exception e) {
                log.error("Stripe refund failed for appointment #{}: {}", appointmentId, e.getMessage());
            }
        }

        String patientEmail = appointment.getPatient().getUser().getEmail();
        auditLogService.log(patientEmail, "APPOINTMENT_CANCELLED",
                "Appointment #" + appointmentId + " cancelled by patient"
                + (reason != null && !reason.isBlank() ? " | Reason: " + reason : ""));
        log.info("Appointment cancelled by patient: id={} patient={} hadPayment={}",
                appointmentId, appointment.getPatient().getFullName(), appointment.isBookingFeePaid());

        notificationService.notifyAppointmentCancelled(appointment, reason);
        activityLogService.saveLog(appointmentId, "APPOINTMENT_CANCELLED",
                "Cancelled by patient"
                + (reason != null && !reason.isBlank() ? " - " + reason : ""));
        metricsService.bookingCancelled();
    }

}
