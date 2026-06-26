package com.example.clinic.exception;
public class PatientNotFoundException extends ClinicException {

    public PatientNotFoundException(String identifier) {
        super("Pacientul nu a fost găsit: " + identifier);
    }
}
