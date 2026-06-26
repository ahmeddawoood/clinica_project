package com.example.clinic.controller;

import com.example.clinic.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppointmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String appointmentNotFound(AppointmentNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("hint", "Programarea poate fi ștearsă sau nu ai acces la ea.");
        return "error/404";
    }

    @ExceptionHandler(PatientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String patientNotFound(PatientNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(DoctorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String doctorNotFound(DoctorNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(DuplicateBookingException.class)
    public String duplicateBooking(DuplicateBookingException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/patient/book";
    }

    @ExceptionHandler(PaymentRequiredException.class)
    public String paymentRequired(PaymentRequiredException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/patient/dashboard";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied(AccessDeniedException ex, Model model) {
        model.addAttribute("message", ex.getMessage() != null ? ex.getMessage()
                : "Nu aveți permisiunea de a accesa această resursă.");
        return "error/403";
    }

    @ExceptionHandler(ClinicException.class)
    public String clinicError(ClinicException ex, Model model) {
        log.warn("Business error: {}", ex.getMessage());
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("hint", "Te rugăm să revii și să încerci din nou.");
        return "error/error";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String illegalState(IllegalStateException ex, Model model) {
        log.warn("Illegal state: {}", ex.getMessage());
        model.addAttribute("message", ex.getMessage());
        model.addAttribute("hint", "Încearcă altă dată sau alt medic.");
        return "error/error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String noResource(NoResourceFoundException ex, Model model) {
        model.addAttribute("message", "Resursa solicitată nu a fost găsită.");
        model.addAttribute("hint", null);
        return "error/404";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpectedError(Exception ex, Model model) {
        log.error("Unexpected error", ex);
        model.addAttribute("message", "A apărut o eroare neașteptată. Te rugăm să încerci din nou.");
        model.addAttribute("hint", null);
        return "error/500";
    }
}
