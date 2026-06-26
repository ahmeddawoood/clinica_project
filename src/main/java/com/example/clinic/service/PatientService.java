package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Patient;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.dto.ProfileForm;

import java.util.List;
public interface PatientService {

    Patient getPatientByEmail(String email);

    List<Appointment> getAppointments(String email);
    Long bookAppointment(AppointmentForm form, String email);
    void cancelAppointment(Long appointmentId, String reason);

    void updateProfile(String email, ProfileForm form);

    List<Patient> getAllPatients();

    Patient getPatientById(Long id);
}
