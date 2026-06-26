package com.example.clinic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class InvoiceForm {

    private Long patientId;
    private Long appointmentId;

    @NotNull(message = "Suma este obligatorie")
    @DecimalMin(value = "0.01", message = "Suma trebuie să fie pozitivă")
    private BigDecimal amount;

    private String description;

    public InvoiceForm() {}

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
