package com.example.clinic.exception;
public class DuplicateBookingException extends ClinicException {

    public DuplicateBookingException(String doctorName, String dateTime) {
        super("Dr. " + doctorName + " are deja o programare la " + dateTime + ".");
    }
}
