package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Rating;
import com.example.clinic.dto.ProfileForm;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public interface DoctorService {

    Doctor getDoctorByEmail(String email);

    List<Appointment> getAppointments(String email);
    void confirmAppointment(Long appointmentId);
    void cancelAppointment(Long appointmentId);
    void completeAppointment(Long appointmentId, String notes);

    void updateProfile(String email, ProfileForm form);

    List<Patient> getPatientsForDoctor(String email);

    List<Doctor> getAllDoctors();

    List<Appointment> getTodayAppointments(String email);

    double getAverageRating(String email);

    long getRatingCount(String email);

    List<Rating> getAllRatings(String email);
    Map<String, Long> getMonthlyStats(String email, int months);
}
