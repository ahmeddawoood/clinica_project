package com.example.clinic.service;

import com.example.clinic.domain.*;
import com.example.clinic.dto.TimelineEvent;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Patient-scoped read model; only the selected history section is materialized. */
@Service
@Transactional(readOnly = true)
public class PatientHistoryService {
    private static final int PAGE_SIZE = 20;
    private static final Set<String> TABS = Set.of("records", "rx", "reports", "invoices", "timeline");
    private final EntityManager em;
    private final NamedParameterJdbcTemplate jdbc;

    public PatientHistoryService(EntityManager em, NamedParameterJdbcTemplate jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    public record HistoryPage(String tab, Page<?> page, Map<String, Long> counts) {}

    public HistoryPage load(Patient patient, String tab, int requestedPage) {
        if (!TABS.contains(tab)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown history section");
        }
        long records = count("MedicalRecord", patient, "");
        long prescriptions = count("Prescription", patient, "");
        long reports = count("MedicalReport", patient, "");
        long invoices = count("Invoice", patient, "");
        long completed = count("Appointment", patient, " AND e.status = 'COMPLETED'");
        Map<String, Long> counts = Map.of("records", records, "rx", prescriptions,
                "reports", reports, "invoices", invoices, "timeline", records + prescriptions + reports + completed);
        long total = counts.get(tab);
        int last = (int) Math.min(Integer.MAX_VALUE / PAGE_SIZE, Math.max(0, (total - 1) / PAGE_SIZE));
        var pageable = PageRequest.of(Math.max(0, Math.min(requestedPage, last)), PAGE_SIZE);
        List<?> content = switch (tab) {
            case "records" -> entities(MedicalRecord.class, "createdAt", patient, pageable);
            case "rx" -> entities(Prescription.class, "issuedAt", patient, pageable);
            case "reports" -> entities(MedicalReport.class, "generatedAt", patient, pageable);
            case "invoices" -> entities(Invoice.class, "issuedAt", patient, pageable);
            default -> timeline(patient, pageable);
        };
        return new HistoryPage(tab, new PageImpl<>(content, pageable, total), counts);
    }

    private long count(String entity, Patient patient, String condition) {
        return em.createQuery("SELECT COUNT(e) FROM " + entity + " e WHERE e.patient = :patient" + condition, Long.class)
                .setParameter("patient", patient).getSingleResult();
    }

    private <T> List<T> entities(Class<T> entity, String dateField, Patient patient, PageRequest page) {
        String fetch = entity == Invoice.class ? "" : " JOIN FETCH e.doctor";
        return em.createQuery("SELECT e FROM " + entity.getSimpleName() + " e" + fetch
                        + " WHERE e.patient = :patient ORDER BY e." + dateField + " DESC, e.id DESC", entity)
                .setParameter("patient", patient).setFirstResult((int) page.getOffset())
                .setMaxResults(PAGE_SIZE).getResultList();
    }

    private List<TimelineEvent> timeline(Patient patient, PageRequest page) {
        // UNION ALL preserves distinct events with identical dates; every branch is owner-scoped.
        String sql = """
            SELECT h.*, CONCAT('Dr. ', COALESCE(d.first_name, ''), ' ', COALESCE(d.last_name, '')) AS doctor_name
            FROM (
                SELECT id, created_at AS event_date, 'RECORD' AS event_type,
                       COALESCE(title, 'Fișă medicală') AS title, diagnosis AS description, doctor_id
                FROM medical_record WHERE patient_id = :patient
                UNION ALL
                SELECT id, issued_at, 'PRESCRIPTION', CONCAT('Rețetă #', id), diagnosis, doctor_id
                FROM prescription WHERE patient_id = :patient
                UNION ALL
                SELECT id, generated_at, 'REPORT', CONCAT('Raport medical #', id), diagnosis, doctor_id
                FROM medical_report WHERE patient_id = :patient
                UNION ALL
                SELECT a.id, a.appointment_date, 'APPOINTMENT', 'Consultație finalizată', d.specialty, a.doctor_id
                FROM appointment a JOIN doctor d ON d.id = a.doctor_id
                WHERE a.patient_id = :patient AND a.status = 'COMPLETED'
            ) h JOIN doctor d ON d.id = h.doctor_id
            ORDER BY h.event_date DESC, h.event_type DESC, h.id DESC
            LIMIT :limit OFFSET :offset
            """;
        return jdbc.query(sql, Map.of("patient", patient.getId(), "limit", PAGE_SIZE, "offset", page.getOffset()),
                (rs, row) -> new TimelineEvent(rs.getTimestamp("event_date") == null ? null
                        : rs.getTimestamp("event_date").toLocalDateTime(), rs.getString("event_type"),
                        rs.getString("title"), rs.getString("doctor_name"), rs.getString("description"), rs.getLong("id")));
    }
}
