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

    @BeforeAll
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        User patientAUser = new User();
        patientAUser.setEmail("security-prescription-patient-a@test.com");
        patientAUser.setPassword("test");
        patientAUser.setRole("PATIENT");
        patientAUser = userRepository.save(patientAUser);

        User patientBUser = new User();
        patientBUser.setEmail("security-prescription-patient-b@test.com");
        patientBUser.setPassword("test");
        patientBUser.setRole("PATIENT");
        patientBUser = userRepository.save(patientBUser);

        User doctorUser = new User();
        doctorUser.setEmail("security-prescription-doctor@test.com");
        doctorUser.setPassword("test");
        doctorUser.setRole("DOCTOR");
        doctorUser = userRepository.save(doctorUser);

        Patient patientA = new Patient();
        patientA.setFirstName("Patient");
        patientA.setLastName("A");
        patientA.setUser(patientAUser);
        patientA = patientRepository.save(patientA);

        Patient patientB = new Patient();
        patientB.setFirstName("Patient");
        patientB.setLastName("B");
        patientB.setUser(patientBUser);
        patientB = patientRepository.save(patientB);

        Doctor doctor = new Doctor();
        doctor.setFirstName("Doctor");
        doctor.setLastName("Test");
        doctor.setSpecialty("General");
        doctor.setUser(doctorUser);
        doctor = doctorRepository.save(doctor);

        patientBPrescription = new Prescription();
        patientBPrescription.setPatient(patientB);
        patientBPrescription.setDoctor(doctor);
        patientBPrescription.setMedications("Test medication");
        patientBPrescription.setDiagnosis("Test diagnosis");
        patientBPrescription.setInstructions("Test instructions");
        patientBPrescription = prescriptionRepository.save(patientBPrescription);
    }

    @Test
    @WithMockUser(username = "patient-a@test.com", roles = "PATIENT")
    void patientCannotDownloadAnotherPatientsPrescription() throws Exception {
        mockMvc.perform(get("/patient/prescriptions/{id}/download", patientBPrescription.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patient-b@test.com", roles = "PATIENT")
    void patientCanDownloadOwnPrescription() throws Exception {
        mockMvc.perform(get("/patient/prescriptions/{id}/download", patientBPrescription.getId()))
                .andExpect(status().isOk());
    }
}
