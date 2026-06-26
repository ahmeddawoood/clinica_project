package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Rating;
import com.example.clinic.domain.User;
import com.example.clinic.dto.ProfileForm;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DoctorNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.RatingRepository;
import com.example.clinic.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DoctorServiceImpl implements DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImpl.class);

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final RatingRepository ratingRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final WebSocketNotificationService wsNotifications;
    private final MetricsService metricsService;

    public DoctorServiceImpl(DoctorRepository doctorRepository,
                              UserRepository userRepository,
                              AppointmentRepository appointmentRepository,
                              RatingRepository ratingRepository,
                              AuditLogService auditLogService,
                              NotificationService notificationService,
                              ActivityLogService activityLogService,
                              WebSocketNotificationService wsNotifications,
                              MetricsService metricsService) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.ratingRepository = ratingRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.wsNotifications = wsNotifications;
        this.metricsService = metricsService;
    }

    @Override
    public Doctor getDoctorByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DoctorNotFoundException(email));
        return doctorRepository.findByUser(user)
                .orElseThrow(() -> new DoctorNotFoundException(email));
    }

    @Override
    public List<Appointment> getAppointments(String email) {
        Doctor doctor = getDoctorByEmail(email);
        return appointmentRepository.findByDoctorOrderByAppointmentDateDesc(doctor);
    }

    @Override
    @Transactional
    public void confirmAppointment(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if ("CONFIRMED".equals(appt.getStatus())) {
            log.info("Appointment {} already confirmed - skipping", appointmentId);
            return;
        }
        if (!"PENDING".equals(appt.getStatus())) {
            throw new ClinicException(
                    "Programarea nu poate fi confirmată (status actual: " + appt.getStatus() + ").");
        }
        if (!appt.isBookingFeePaid()) {
            throw new ClinicException("Programarea nu poate fi confirmată: taxa de rezervare nu a fost achitată.");
        }
        appt.setStatus("CONFIRMED");
        appointmentRepository.save(appt);

        String doctorEmail = appt.getDoctor().getUser().getEmail();
        auditLogService.log(doctorEmail, "APPOINTMENT_CONFIRMED",
                "Programare #" + appointmentId + " confirmată | Pacient: " + appt.getPatient().getFullName()
                + " | Data: " + appt.getAppointmentDate());
        log.info("Appointment confirmed: id={} patient={} doctor={}",
                appointmentId, appt.getPatient().getFullName(), appt.getDoctor().getFullName());

        notificationService.notifyAppointmentConfirmed(appt);
        wsNotifications.notifyPatient(appt, "CONFIRMED",
                "Programarea ta a fost confirmată pentru " + appt.getAppointmentDate().toLocalDate());
        activityLogService.saveLog(appointmentId, "DOCTOR_CONFIRMED",
                "Confirmat de Dr. " + appt.getDoctor().getFullName());
    }

    @Override
    @Transactional
    public void cancelAppointment(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if ("CANCELLED".equals(appt.getStatus())) {
            log.info("Appointment {} already cancelled - skipping", appointmentId);
            return;
        }
        if ("COMPLETED".equals(appt.getStatus())) {
            throw new ClinicException("O programare finalizată nu poate fi anulată.");
        }
        appt.setStatus("CANCELLED");
        appointmentRepository.save(appt);

        String doctorEmail = appt.getDoctor().getUser().getEmail();
        auditLogService.log(doctorEmail, "APPOINTMENT_CANCELLED",
                "Programare #" + appointmentId + " anulată de doctor | Pacient: " + appt.getPatient().getFullName());
        log.info("Appointment cancelled by doctor: id={} patient={}", appointmentId, appt.getPatient().getFullName());

        notificationService.notifyAppointmentCancelled(appt, "Anulat de medicul curant.");
        wsNotifications.notifyPatient(appt, "CANCELLED",
                "Programarea ta a fost anulată de Dr. " + appt.getDoctor().getFullName());
        activityLogService.saveLog(appointmentId, "APPOINTMENT_CANCELLED",
                "Anulată de Dr. " + appt.getDoctor().getFullName());
        metricsService.bookingCancelled();
    }

    @Override
    @Transactional
    public void completeAppointment(Long appointmentId, String notes) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if ("COMPLETED".equals(appt.getStatus())) {
            log.info("Appointment {} already completed - skipping", appointmentId);
            return;
        }
        if (!"CONFIRMED".equals(appt.getStatus())) {
            throw new ClinicException(
                    "Doar programările confirmate pot fi finalizate (status: " + appt.getStatus() + ").");
        }
        appt.setStatus("COMPLETED");
        if (notes != null && !notes.isBlank()) appt.setNotes(notes);
        appointmentRepository.save(appt);

        String doctorEmail = appt.getDoctor().getUser().getEmail();
        auditLogService.log(doctorEmail, "APPOINTMENT_COMPLETED",
                "Programare #" + appointmentId + " finalizată | Pacient: " + appt.getPatient().getFullName()
                + (notes != null && !notes.isBlank() ? " | Note: " + notes : ""));
        log.info("Appointment completed: id={} doctor={} patient={}",
                appointmentId, appt.getDoctor().getFullName(), appt.getPatient().getFullName());

        notificationService.notifyAppointmentCompleted(appt);
        metricsService.bookingCompleted();
        wsNotifications.notifyPatient(appt, "COMPLETED",
                "Consultația cu Dr. " + appt.getDoctor().getFullName() + " a fost finalizată.");
        activityLogService.saveLog(appointmentId, "APPOINTMENT_COMPLETED",
                "Consultație finalizată de Dr. " + appt.getDoctor().getFullName()
                + (notes != null && !notes.isBlank() ? " - " + notes : ""));
    }

    @Override
    @Transactional
    public void updateProfile(String email, ProfileForm form) {
        Doctor doctor = getDoctorByEmail(email);
        doctor.setFirstName(form.getFirstName());
        doctor.setLastName(form.getLastName());
        doctor.setPhone(form.getPhone());
        doctor.setSpecialty(form.getSpecialty());
        doctorRepository.save(doctor);
        log.info("Doctor profile updated: email={}", email);
    }

    @Override
    public List<Patient> getPatientsForDoctor(String email) {
        Doctor doctor = getDoctorByEmail(email);
        return appointmentRepository.findByDoctor(doctor).stream()
                .map(Appointment::getPatient)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Doctor> getAllDoctors() {
        return doctorRepository.findAll();
    }

    @Override
    public List<Appointment> getTodayAppointments(String email) {
        Doctor doctor = getDoctorByEmail(email);
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return appointmentRepository.findTodayByDoctor(doctor, start, end);
    }

    @Override
    public double getAverageRating(String email) {
        Doctor doctor = getDoctorByEmail(email);
        Double avg = ratingRepository.averageByDoctor(doctor);
        if (avg == null) return 0.0;
        return BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public long getRatingCount(String email) {
        return ratingRepository.countByDoctor(getDoctorByEmail(email));
    }

    @Override
    public List<Rating> getAllRatings(String email) {
        return ratingRepository.findByDoctorOrderByCreatedAtDesc(getDoctorByEmail(email));
    }

    @Override
    public Map<String, Long> getMonthlyStats(String email, int months) {
        List<Appointment> all = getAppointments(email);
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime month = LocalDateTime.now().minusMonths(i);
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.US)
                    + " " + month.getYear();
            long count = all.stream()
                .filter(a -> a.getAppointmentDate().getMonth() == month.getMonth()
                          && a.getAppointmentDate().getYear()  == month.getYear())
                .count();
            result.put(label, count);
        }
        return result;
    }
}
