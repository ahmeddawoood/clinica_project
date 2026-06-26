package com.example.clinic.exception;
public class PaymentRequiredException extends ClinicException {

    public PaymentRequiredException(Long appointmentId) {
        super("Plata este necesară pentru confirmarea programării #" + appointmentId + ".");
    }
}
