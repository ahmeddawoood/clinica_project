package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy 'la' HH:mm", new Locale("ro", "RO"));
    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:noreply@mediclinic.ro}")
    private String fromEmail;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.phone-number:}")
    private String twilioFromPhone;

    @Value("${app.sms.enabled:false}")
    private boolean smsEnabled;

    private volatile boolean twilioInitialized = false;

    private final MetricsService metricsService;

    public NotificationServiceImpl(TemplateEngine templateEngine, MetricsService metricsService) {
        this.templateEngine = templateEngine;
        this.metricsService = metricsService;
    }

    @Override
    @Async
    public void notifyBookingCreated(Appointment appt) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Programare creată - finalizați plata | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/booking-confirmation", buildVars(appt, null));
        sendSms(appt.getPatient().getPhone(),
                "MediClinic: Programarea ta cu Dr. " + appt.getDoctor().getFullName()
                + " pe " + appt.getAppointmentDate().format(DATE_FMT)
                + " a fost creată. Finalizați plata pentru confirmare. #" + appt.getId());
    }

    @Override
    @Async
    public void notifyPaymentSuccess(Appointment appt) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Plată confirmată - programare activă | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/payment-success", buildVars(appt, null));
        sendSms(appt.getPatient().getPhone(),
                "MediClinic: Plată confirmată! Programarea #" + appt.getId()
                + " cu Dr. " + appt.getDoctor().getFullName() + " este activă.");
    }

    @Override
    @Async
    public void notifyAppointmentConfirmed(Appointment appt) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Programare confirmată | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/appointment-confirmed", buildVars(appt, null));
        sendSms(appt.getPatient().getPhone(),
                "MediClinic: Programarea #" + appt.getId() + " cu Dr. " + appt.getDoctor().getFullName()
                + " pe " + appt.getAppointmentDate().format(DATE_FMT) + " a fost CONFIRMATĂ.");
    }

    @Override
    @Async
    public void notifyAppointmentCompleted(Appointment appt) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Consultație finalizată | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/appointment-completed", buildVars(appt, null));
    }

    @Override
    @Async
    public void notifyAppointmentCancelled(Appointment appt, String reason) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Programare anulată | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/appointment-cancelled", buildVars(appt, reason));
        sendSms(appt.getPatient().getPhone(),
                "MediClinic: Programarea #" + appt.getId() + " cu Dr. " + appt.getDoctor().getFullName()
                + " a fost anulată." + (reason != null && !reason.isBlank() ? " Motiv: " + reason : ""));
    }

    @Override
    @Async
    public void notifyRefund(Appointment appt) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Rambursare procesată | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/refund-notification", buildVars(appt, null));
        sendSms(appt.getPatient().getPhone(),
                "MediClinic: Taxa de rezervare pentru programarea #" + appt.getId()
                + " a fost rambursată. Verificați contul în 3-5 zile lucrătoare.");
    }

    @Override
    @Async
    public void notifyAppointmentReminder(Appointment appt) {
        String patientEmail = appt.getPatient().getUser().getEmail();
        String subject = "Reminder: programare mâine | MediClinic #" + appt.getId();
        sendEmail(patientEmail, subject, "email/appointment-reminder", buildVars(appt, null));
        sendSms(appt.getPatient().getPhone(),
                "MediClinic: Reminder - programare mâine cu Dr. " + appt.getDoctor().getFullName()
                + " la ora " + appt.getAppointmentDate().toLocalTime()
                + ". Vă rugăm să fiți prezent cu 10 min înainte.");
    }

    @Override
    public void sendEmail(String to, String subject, String template, Map<String, Object> vars) {
        if (!mailEnabled || mailSender == null) {
            log.debug("Email skipped (disabled or not configured): to={} subject={}", to, subject);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariables(vars);
            String html = templateEngine.process(template, ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, "MediClinic", "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);

            metricsService.emailSent();
            log.info("Email sent: to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email: to={} subject={} error={}", to, subject, e.getMessage());
        }
    }

    @Override
    public void sendSms(String phone, String body) {
        if (!smsEnabled || twilioAccountSid.isBlank() || phone == null || phone.isBlank()) {
            log.debug("SMS skipped (disabled or not configured): phone={}", phone);
            return;
        }
        try {
            initTwilio();
            String to = toE164(phone);
            Message.creator(new PhoneNumber(to), new PhoneNumber(twilioFromPhone), body).create();
            metricsService.smsSent();
            log.info("SMS sent: to={}", to);
        } catch (Exception e) {
            log.error("Failed to send SMS: phone={} error={}", phone, e.getMessage());
        }
    }

    private synchronized void initTwilio() {
        if (!twilioInitialized) {
            Twilio.init(twilioAccountSid, twilioAuthToken);
            twilioInitialized = true;
        }
    }
    private String toE164(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("0")) return "+4" + digits;
        return "+" + digits;
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "Resetare parolă | MediClinic";
        if (!mailEnabled || mailSender == null) {
            log.info("Password reset link (email disabled) -> {}", resetLink);
            return;
        }
        sendEmail(to, subject, "email/password-reset", Map.of("resetLink", resetLink, "expiryHours", 1));
    }

    private Map<String, Object> buildVars(Appointment appt, String reason) {
        return Map.of(
                "patientName",   appt.getPatient().getFullName(),
                "doctorName",    "Dr. " + appt.getDoctor().getFullName(),
                "specialty",     appt.getDoctor().getSpecialty(),
                "appointmentId", appt.getId(),
                "dateTime",      appt.getAppointmentDate().format(DATE_FMT),
                "status",        appt.getStatus(),
                "reason",        reason != null ? reason : ""
        );
    }
}
