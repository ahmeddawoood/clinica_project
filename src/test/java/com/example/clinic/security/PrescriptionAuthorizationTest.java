package com.example.clinic.security;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Prescription;
import com.example.clinic.domain.User;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.PrescriptionRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrescriptionAuthorizationTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired WebApplicationContext wac;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PrescriptionRepository prescriptionRepository;

    private MockMvc mockMvc;
    private Prescription patientBPrescription;
    private Prescription anotherDoctorsPrescription;

    @BeforeAll
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User patientAUser = user("security-prescription-patient-a@test.com", "PATIENT");
        User patientBUser = user("security-prescription-patient-b@test.com", "PATIENT");
        User doctorUser = user("security-prescription-doctor@test.com", "DOCTOR");
        User anotherDoctorUser = user("security-prescription-doctor-b@test.com", "DOCTOR");

        Patient patientA = patient("Patient", "A", patientAUser);
        Patient patientB = patient("Patient", "B", patientBUser);

        Doctor doctor = doctor("Doctor", "Test", doctorUser);
        Doctor anotherDoctor = doctor("Doctor", "B", anotherDoctorUser);

        patientBPrescription = new Prescription();
        patientBPrescription.setPatient(patientB);
        patientBPrescription.setDoctor(doctor);
        patientBPrescription.setMedications("Test medication");
        patientBPrescription.setDiagnosis("Test diagnosis");
        patientBPrescription.setInstructions("Test instructions");
        patientBPrescription = prescriptionRepository.save(patientBPrescription);

        anotherDoctorsPrescription = new Prescription();
        anotherDoctorsPrescription.setPatient(patientA);
        anotherDoctorsPrescription.setDoctor(anotherDoctor);
        anotherDoctorsPrescription.setMedications("Doctor B medication");
        anotherDoctorsPrescription.setDiagnosis("Doctor B diagnosis");
        anotherDoctorsPrescription.setInstructions("Doctor B instructions");
        anotherDoctorsPrescription = prescriptionRepository.save(anotherDoctorsPrescription);
    }

    private User user(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("test");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Patient patient(String firstName, String lastName, User user) {
        Patient patient = new Patient();
        patient.setFirstName(firstName);
        patient.setLastName(lastName);
        patient.setUser(user);
        return patientRepository.save(patient);
    }

    private Doctor doctor(String firstName, String lastName, User user) {
        Doctor doctor = new Doctor();
        doctor.setFirstName(firstName);
        doctor.setLastName(lastName);
        doctor.setSpecialty("General");
        doctor.setUser(user);
        return doctorRepository.save(doctor);
    }

    @Test
    @WithMockUser(username = "security-prescription-patient-a@test.com", roles = "PATIENT")
    void patientCannotDownloadAnotherPatientsPrescription() throws Exception {
        mockMvc.perform(get("/patient/prescriptions/{id}/download", patientBPrescription.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "security-prescription-patient-b@test.com", roles = "PATIENT")
    void patientCanDownloadOwnPrescription() throws Exception {
        mockMvc.perform(get("/patient/prescriptions/{id}/download", patientBPrescription.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "security-prescription-doctor@test.com", roles = "DOCTOR")
    void doctorCannotDownloadAnotherDoctorsPrescription() throws Exception {
        mockMvc.perform(get("/doctor/prescriptions/{id}/download", anotherDoctorsPrescription.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "security-prescription-doctor@test.com", roles = "DOCTOR")
    void doctorCanDownloadOwnPrescription() throws Exception {
        mockMvc.perform(get("/doctor/prescriptions/{id}/download", patientBPrescription.getId()))
                .andExpect(status().isOk());
    }
}
