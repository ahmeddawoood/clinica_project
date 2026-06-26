package com.example.clinic.exception;
public class AppointmentNotFoundException extends ClinicException {

    public AppointmentNotFoundException(Long id) {
        super("Programarea #" + id + " nu a fost găsită.");
    }
}
