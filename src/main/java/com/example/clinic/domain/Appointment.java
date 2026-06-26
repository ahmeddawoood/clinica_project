package com.example.clinic.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    private LocalDateTime appointmentDate;
    private String status;

    private String notes;
    private String cancelReason;
    private String priority = "LOW";

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private boolean bookingFeePaid = false;
    private boolean bookingFeeRefunded = false;

    @Column(length = 500)
    private String stripePaymentIntentId;

    @Column(length = 500)
    private String stripeSessionId;
    private String stripePaymentStatus;

    private Long stripeAmountCents;
    private String stripeCurrency;
    private LocalDateTime stripePaidAt;

    @Column(length = 500)
    private String stripeRefundId;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public Appointment() {}

    public Long getId() { return id; }

    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }

    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }

    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isBookingFeePaid() { return bookingFeePaid; }
    public void setBookingFeePaid(boolean bookingFeePaid) { this.bookingFeePaid = bookingFeePaid; }

    public boolean isBookingFeeRefunded() { return bookingFeeRefunded; }
    public void setBookingFeeRefunded(boolean bookingFeeRefunded) { this.bookingFeeRefunded = bookingFeeRefunded; }

    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String stripePaymentIntentId) { this.stripePaymentIntentId = stripePaymentIntentId; }

    public String getStripeSessionId() { return stripeSessionId; }
    public void setStripeSessionId(String stripeSessionId) { this.stripeSessionId = stripeSessionId; }

    public String getStripePaymentStatus() { return stripePaymentStatus; }
    public void setStripePaymentStatus(String stripePaymentStatus) { this.stripePaymentStatus = stripePaymentStatus; }

    public Long getStripeAmountCents() { return stripeAmountCents; }
    public void setStripeAmountCents(Long stripeAmountCents) { this.stripeAmountCents = stripeAmountCents; }

    public String getStripeCurrency() { return stripeCurrency; }
    public void setStripeCurrency(String stripeCurrency) { this.stripeCurrency = stripeCurrency; }

    public LocalDateTime getStripePaidAt() { return stripePaidAt; }
    public void setStripePaidAt(LocalDateTime stripePaidAt) { this.stripePaidAt = stripePaidAt; }

    public String getStripeRefundId() { return stripeRefundId; }
    public void setStripeRefundId(String stripeRefundId) { this.stripeRefundId = stripeRefundId; }
}
