package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.MedicalReport;
import com.example.clinic.exception.AppointmentNotFoundException;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.MedicalReportRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", Locale.of("ro", "RO"));
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("ro", "RO"));
    private static final Color BRAND_BLUE   = new Color(29, 78, 216);   
    private static final Color BRAND_TEAL   = new Color(42, 157, 143);  
    private static final Color LIGHT_GRAY   = new Color(248, 250, 252); 
    private static final Color BORDER_GRAY  = new Color(226, 232, 240); 
    private static final Color TEXT_DARK    = new Color(15, 23, 42);    
    private static final Color TEXT_MUTED   = new Color(100, 116, 139); 
    private static final Color SUCCESS_GREEN= new Color(22, 163, 74);   

    private final AppointmentRepository appointmentRepository;
    private final MedicalReportRepository medicalReportRepository;

    public ReportServiceImpl(AppointmentRepository appointmentRepository,
                              MedicalReportRepository medicalReportRepository) {
        this.appointmentRepository = appointmentRepository;
        this.medicalReportRepository = medicalReportRepository;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('DOCTOR')")
    public MedicalReport generate(Long appointmentId, String diagnosis,
                                   String treatment, String notes) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        byte[] pdf = buildPdf(appt, diagnosis, treatment, notes);

        MedicalReport report = new MedicalReport();
        report.setAppointment(appt);
        report.setDoctor(appt.getDoctor());
        report.setPatient(appt.getPatient());
        report.setDiagnosis(diagnosis);
        report.setTreatment(treatment);
        report.setNotes(notes);
        report.setPdfData(pdf);

        MedicalReport saved = medicalReportRepository.save(report);
        log.info("Medical report generated: id={} appointmentId={} doctor={} patient={}",
                saved.getId(), appointmentId,
                appt.getDoctor().getFullName(), appt.getPatient().getFullName());
        return saved;
    }

    @Override
    public MedicalReport findById(Long reportId) {
        return medicalReportRepository.findById(reportId)
                .orElseThrow(() -> new ClinicException("Raportul nu există: " + reportId));
    }

    @Override
    public List<MedicalReport> findByAppointment(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        return medicalReportRepository.findByAppointmentOrderByGeneratedAtDesc(appt);
    }

    private byte[] buildPdf(Appointment appt, String diagnosis,
                             String treatment, String notes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4, 50, 50, 50, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterPageEvent());

            doc.open();
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{3f, 1.5f});
            header.setSpacingAfter(20);
            PdfPCell clinicCell = new PdfPCell();
            clinicCell.setBorder(Rectangle.NO_BORDER);
            clinicCell.setPaddingBottom(14);
            clinicCell.addElement(new Phrase("MediClinic",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND_BLUE)));
            clinicCell.addElement(new Phrase("Raport Medical Oficial",
                    FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_MUTED)));
            header.addCell(clinicCell);
            PdfPCell badgeCell = new PdfPCell();
            badgeCell.setBorder(Rectangle.NO_BORDER);
            badgeCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            badgeCell.setPaddingBottom(14);
            badgeCell.addElement(new Phrase("Raport #" + appt.getId(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_TEAL)));
            badgeCell.addElement(new Phrase(appt.getAppointmentDate().format(DATE_ONLY),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
            header.addCell(badgeCell);

            doc.add(header);
            PdfPTable divider = new PdfPTable(1);
            divider.setWidthPercentage(100);
            divider.setSpacingAfter(20);
            PdfPCell divCell = new PdfPCell(new Phrase(" "));
            divCell.setFixedHeight(3f);
            divCell.setBackgroundColor(BRAND_BLUE);
            divCell.setBorder(Rectangle.NO_BORDER);
            divider.addCell(divCell);
            doc.add(divider);
            doc.add(sectionTitle("Detalii Programare"));
            doc.add(infoTable(new String[][]{
                    {"Pacient",    appt.getPatient().getFullName()},
                    {"Medic",      "Dr. " + appt.getDoctor().getFullName()},
                    {"Specialitate", appt.getDoctor().getSpecialty()},
                    {"Data consultației", appt.getAppointmentDate().format(DATE_FMT)},
                    {"Nr. programare", "#" + appt.getId()},
                    {"Status",     appt.getStatus()}
            }));
            doc.add(sectionTitle("Diagnostic"));
            doc.add(contentBox(diagnosis));
            if (treatment != null && !treatment.isBlank()) {
                doc.add(sectionTitle("Tratament recomandat"));
                doc.add(contentBox(treatment));
            }
            if (notes != null && !notes.isBlank()) {
                doc.add(sectionTitle("Observații și recomandări"));
                doc.add(contentBox(notes));
            }
            doc.add(Chunk.NEWLINE);
            doc.add(signatureBlock(appt));
            doc.add(Chunk.NEWLINE);
            Paragraph disclaimer = new Paragraph(
                    "Acest document este generat electronic de sistemul MediClinic și are valoare medicală oficială.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, TEXT_MUTED));
            disclaimer.setAlignment(Element.ALIGN_CENTER);
            doc.add(disclaimer);

        } catch (DocumentException e) {
            throw new ClinicException("Eroare la generarea PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_BLUE));
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPTable infoTable(String[][] rows) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.6f, 3f});
        table.setSpacingAfter(8);

        boolean alt = false;
        for (String[] row : rows) {
            Color bg = alt ? LIGHT_GRAY : Color.WHITE;

            PdfPCell labelCell = new PdfPCell(new Phrase(row[0],
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_MUTED)));
            labelCell.setBackgroundColor(bg);
            labelCell.setBorderColor(BORDER_GRAY);
            labelCell.setPadding(7);

            PdfPCell valueCell = new PdfPCell(new Phrase(row[1],
                    FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_DARK)));
            valueCell.setBackgroundColor(bg);
            valueCell.setBorderColor(BORDER_GRAY);
            valueCell.setPadding(7);

            table.addCell(labelCell);
            table.addCell(valueCell);
            alt = !alt;
        }
        return table;
    }

    private PdfPTable contentBox(String text) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(6);

        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA, 10, TEXT_DARK)));
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(12);
        cell.setLeading(0, 1.5f);
        table.addCell(cell);
        return table;
    }

    private PdfPTable signatureBlock(Appointment appt) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingBefore(20);
        PdfPCell doctorSig = new PdfPCell();
        doctorSig.setBorder(Rectangle.NO_BORDER);
        doctorSig.addElement(new Phrase("Medic curant",
                FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
        doctorSig.addElement(new Phrase("Dr. " + appt.getDoctor().getFullName(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK)));
        doctorSig.addElement(new Phrase(appt.getDoctor().getSpecialty(),
                FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
        doctorSig.addElement(new Phrase("\n_________________________",
                FontFactory.getFont(FontFactory.HELVETICA, 9, BORDER_GRAY)));
        table.addCell(doctorSig);
        PdfPCell dateSig = new PdfPCell();
        dateSig.setBorder(Rectangle.NO_BORDER);
        dateSig.setHorizontalAlignment(Element.ALIGN_RIGHT);
        dateSig.addElement(new Phrase("Data emiterii",
                FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
        dateSig.addElement(new Phrase(appt.getAppointmentDate().format(DATE_ONLY),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, TEXT_DARK)));
        dateSig.addElement(new Phrase("\nStampila clinicii",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, BORDER_GRAY)));
        table.addCell(dateSig);

        return table;
    }

    private static class FooterPageEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            BaseFont bf;
            try {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                return;
            }
            cb.saveState();
            cb.setColorFill(new Color(100, 116, 139));
            cb.setFontAndSize(bf, 8);

            String left = "MediClinic - Raport Medical Confidential";
            String right = "Pagina " + writer.getPageNumber();
            float y = document.bottom() - 15;

            cb.beginText();
            cb.showTextAligned(PdfContentByte.ALIGN_LEFT, left, document.left(), y, 0);
            cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, right, document.right(), y, 0);
            cb.endText();
            cb.restoreState();
        }
    }
}
