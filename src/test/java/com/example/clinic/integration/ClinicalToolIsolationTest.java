package com.example.clinic.integration;

import com.example.clinic.ai.MyMedicalHistoryTool;
import com.example.clinic.ai.MyPrescriptionsTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClinicalToolIsolationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MyMedicalHistoryTool history;
    @Autowired MyPrescriptionsTool prescriptions;

    @Test
    @WithMockUser(username = "isolation-a@example.test", roles = "PATIENT")
    void realQueriesReturnOnlySignedInPatientsRecords() {
        long a = patient("isolation-a@example.test");
        long b = patient("isolation-b@example.test");
        jdbc.update("INSERT INTO doctor (first_name,last_name,specialty) VALUES ('Test','Doctor','Cardiology')");
        long doctor = jdbc.queryForObject("SELECT id FROM doctor WHERE first_name='Test' AND last_name='Doctor'", Long.class);
        record(a, doctor, "own-record", "own-medication");
        record(b, doctor, "other-record", "other-medication");

        assertThat(history.getMyMedicalHistory()).extracting(r -> r.title())
                .containsExactly("own-record");
        assertThat(prescriptions.getMyPrescriptions()).extracting(r -> r.medications())
                .containsExactly("own-medication");
    }

    private long patient(String email) {
        jdbc.update("INSERT INTO app_user (email,password,role,enabled) VALUES (?, 'fixture', 'PATIENT', true)", email);
        long user = jdbc.queryForObject("SELECT id FROM app_user WHERE email=?", Long.class, email);
        jdbc.update("INSERT INTO patient (first_name,last_name,user_id) VALUES ('Test','Patient',?)", user);
        return jdbc.queryForObject("SELECT id FROM patient WHERE user_id=?", Long.class, user);
    }

    private void record(long patient, long doctor, String title, String medication) {
        jdbc.update("INSERT INTO medical_record (patient_id,doctor_id,title,created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", patient, doctor, title);
        jdbc.update("INSERT INTO prescription (patient_id,doctor_id,medications,issued_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", patient, doctor, medication);
    }
}
