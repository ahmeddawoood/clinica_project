package com.example.clinic.exception;
public class DoctorNotFoundException extends ClinicException {

    public DoctorNotFoundException(String identifier) {
        super("Medicul nu a fost găsit: " + identifier);
    }
}
