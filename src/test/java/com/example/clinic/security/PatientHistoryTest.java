package com.example.clinic.security;

import com.example.clinic.domain.*;
import com.example.clinic.dto.TimelineEvent;
import com.example.clinic.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PatientHistoryTest {
    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;
    @Autowired EntityManager em;
    @Autowired PatientHistoryService service;
    @Autowired WebApplicationContext context;
    Patient owner;
    Patient other;
    Doctor doctor;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        doctor = new Doctor(); doctor.setFirstName("History"); doctor.setLastName("Doctor"); em.persist(doctor);
        owner = patient("history@example.test");
        other = patient("private@example.test");
        for (int i = 0; i < 21; i++) record(owner, "Owner record " + i);
        record(other, "PRIVATE-OTHER-PATIENT");
        extraEvents(owner, "OWNER-DIAGNOSIS");
        extraEvents(other, "PRIVATE-OTHER-PATIENT");
        em.flush();
        // Equal timestamps exercise the deterministic id tie-breaker.
        em.createNativeQuery("UPDATE medical_record SET created_at = '2026-01-01 10:00:00'").executeUpdate();
        em.clear();
    }

    Patient patient(String email) {
        var user = new User(); user.setEmail(email); user.setPassword("hashed"); user.setRole("PATIENT"); em.persist(user);
        var patient = new Patient(); patient.setUser(user); patient.setFirstName("History"); patient.setLastName("Patient");
        em.persist(patient); return patient;
    }

    void record(Patient patient, String title) {
        var record = new MedicalRecord(); record.setPatient(patient); record.setDoctor(doctor); record.setTitle(title);
        record.setDiagnosis(title); em.persist(record);
    }

    void extraEvents(Patient patient, String diagnosis) {
        var a = new Appointment(); a.setPatient(patient); a.setDoctor(doctor); a.setStatus("COMPLETED");
        a.setAppointmentDate(LocalDateTime.of(2026, 1, 2, 10, 0)); em.persist(a);
        var rx = new Prescription(); rx.setPatient(patient); rx.setDoctor(doctor); rx.setMedications("Fixture");
        rx.setDiagnosis(diagnosis); em.persist(rx);
        var report = new MedicalReport(); report.setPatient(patient); report.setDoctor(doctor); report.setAppointment(a);
        report.setDiagnosis(diagnosis); em.persist(report);
        var invoice = new Invoice(); invoice.setPatient(patient); invoice.setAmount(BigDecimal.TEN); invoice.setStatus("PAID");
        invoice.setDescription(diagnosis); em.persist(invoice);
    }

    @Test void pagesAreBoundedAndStableAndClampInvalidOffsets() {
        var first = service.load(owner, "records", 0).page();
        var second = service.load(owner, "records", 1).page();
        assertThat(first.getTotalElements()).isEqualTo(21);
        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(1);
        var ids = first.getContent().stream().map(x -> ((MedicalRecord) x).getId()).toList();
        assertThat(ids).doesNotContain(((MedicalRecord) second.getContent().getFirst()).getId());
        assertThat(service.load(owner, "records", Integer.MAX_VALUE).page().getNumber()).isEqualTo(1);
        assertThat(service.load(owner, "records", -9).page().getNumber()).isZero();
    }

    @Test void mergedTimelineIsPagedAndCountsOnlyOwnerEvents() {
        var first = service.load(owner, "timeline", 0).page();
        var second = service.load(owner, "timeline", 1).page();
        assertThat(first.getTotalElements()).isEqualTo(24);
        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(4);
        var events = java.util.stream.Stream.concat(first.getContent().stream(), second.getContent().stream())
                .map(TimelineEvent.class::cast).toList();
        assertThat(events).extracting(TimelineEvent::getType).contains("RECORD", "PRESCRIPTION", "REPORT", "APPOINTMENT");
        assertThat(events).extracting(TimelineEvent::getDescription).doesNotContain("PRIVATE-OTHER-PATIENT");
        assertThat(events.stream().map(e -> e.getType() + e.getEntityId()).distinct().count()).isEqualTo(24);
    }

    @Test @WithMockUser(username = "history@example.test", roles = "PATIENT")
    void allSectionsRenderAndIgnoreForgedPatientIdentifier() throws Exception {
        for (String tab : List.of("records", "rx", "reports", "invoices", "timeline")) {
            mvc.perform(get("/patient/history").param("tab", tab).param("patientId", other.getId().toString()))
                    .andExpect(status().isOk()).andExpect(model().attribute("activeTab", tab))
                    .andExpect(content().string(not(containsString("PRIVATE-OTHER-PATIENT"))));
        }
    }

    @Test @WithMockUser(username = "history@example.test", roles = "PATIENT")
    void invalidSectionAndPageReturnBadRequest() throws Exception {
        mvc.perform(get("/patient/history").param("tab", "other")).andExpect(status().isBadRequest());
        mvc.perform(get("/patient/history").param("page", "abc")).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(roles = "DOCTOR")
    void doctorCannotEnterPatientHistoryRoute() throws Exception {
        mvc.perform(get("/patient/history")).andExpect(status().isForbidden());
    }
}
