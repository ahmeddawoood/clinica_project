package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.AppointmentForm;
import com.example.clinic.dto.ProfileForm;
import com.example.clinic.dto.SymptomAnalysisResult;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DuplicateBookingException;
import com.example.clinic.exception.PatientNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PatientServiceImpl implements PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientServiceImpl.class);

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentBookingService bookingService;

    public PatientServiceImpl(PatientRepository patientRepository, UserRepository userRepository,
            AppointmentRepository appointmentRepository, AppointmentBookingService bookingService) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.bookingService = bookingService;
    }

    @Override
    public Patient getPatientByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new PatientNotFoundException(email));
        return patientRepository.findByUser(user)
                .orElseThrow(() -> new PatientNotFoundException(email));
    }

    @Override
    public Patient getPatientById(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException("id=" + id));
    }

    @Override
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @Override
    public List<Appointment> getAppointments(String email) {
        Patient patient = getPatientByEmail(email);
        return appointmentRepository.findByPatientOrderByAppointmentDateDesc(patient);
    }

    @Override
    public Long bookAppointment(AppointmentForm form, String email) {
        return bookingService.bookAppointment(form, email);
    }

    @Override
    public void cancelAppointment(Long appointmentId, String reason) {
        bookingService.cancelAppointment(appointmentId, reason);
    }

    @Override
    @Transactional
    public void updateProfile(String email, ProfileForm form) {
        Patient patient = getPatientByEmail(email);
        patient.setFirstName(form.getFirstName());
        patient.setLastName(form.getLastName());
        patient.setPhone(form.getPhone());
        patient.setGender(form.getGender());
        patient.setDateOfBirth(form.getDateOfBirth());
        patient.setBloodType(form.getBloodType());
        patient.setAllergies(form.getAllergies());
        patient.setAddress(form.getAddress());
        patientRepository.save(patient);
        log.info("Patient profile updated: email={}", email);
    }
}
