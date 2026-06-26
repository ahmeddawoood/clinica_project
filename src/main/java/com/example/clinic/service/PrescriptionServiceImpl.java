package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Prescription;
import com.example.clinic.dto.PrescriptionForm;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DoctorNotFoundException;
import com.example.clinic.exception.PatientNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.PrescriptionRepository;
import com.example.clinic.repository.UserRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public PrescriptionServiceImpl(PrescriptionRepository prescriptionRepository,
                                    PatientRepository patientRepository,
                                    DoctorRepository doctorRepository,
                                    AppointmentRepository appointmentRepository,
                                    UserRepository userRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Prescription> getPrescriptionsByPatientEmail(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new PatientNotFoundException(email));
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new PatientNotFoundException(email));
        return prescriptionRepository.findByPatientOrderByIssuedAtDesc(patient);
    }

    @Override
    public List<Prescription> getPrescriptionsByDoctorEmail(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DoctorNotFoundException(email));
        Doctor doctor = doctorRepository.findByUser(user)
                .orElseThrow(() -> new DoctorNotFoundException(email));
        return prescriptionRepository.findByDoctorOrderByIssuedAtDesc(doctor);
    }

    @Override
    @Transactional(readOnly = true)
    public Prescription getPrescriptionById(Long id) {
        return prescriptionRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new ClinicException("Rețeta nu există: " + id));
    }

    @Override
    @Transactional
    public void writePrescription(PrescriptionForm form, String doctorEmail) {
        var user = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new DoctorNotFoundException(doctorEmail));
        Doctor doctor = doctorRepository.findByUser(user)
                .orElseThrow(() -> new DoctorNotFoundException(doctorEmail));
        Patient patient = patientRepository.findById(form.getPatientId())
                .orElseThrow(() -> new PatientNotFoundException("id=" + form.getPatientId()));

        Prescription prescription = new Prescription();
        prescription.setDoctor(doctor);
        prescription.setPatient(patient);
        prescription.setDiagnosis(form.getDiagnosis());
        prescription.setMedications(form.getMedications());
        prescription.setInstructions(form.getInstructions());

        if (form.getAppointmentId() != null) {
            Appointment appointment = appointmentRepository.findById(form.getAppointmentId()).orElse(null);
            prescription.setAppointment(appointment);
        }

        prescriptionRepository.save(prescription);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long prescriptionId) {
        Prescription rx = getPrescriptionById(prescriptionId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("ro", "RO"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 60);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Color BLUE  = new Color(29, 78, 216);
            Color TEAL  = new Color(42, 157, 143);
            Color GRAY  = new Color(100, 116, 139);
            Color LGRAY = new Color(248, 250, 252);
            Color BORD  = new Color(226, 232, 240);
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{3f, 1.5f});
            header.setSpacingAfter(16);
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.addElement(new Phrase("MediClinic", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BLUE)));
            left.addElement(new Phrase("Rețetă Medicală", FontFactory.getFont(FontFactory.HELVETICA, 10, GRAY)));
            header.addCell(left);
            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            right.addElement(new Phrase("Nr. " + rx.getId(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TEAL)));
            right.addElement(new Phrase(rx.getIssuedAt().format(fmt), FontFactory.getFont(FontFactory.HELVETICA, 9, GRAY)));
            header.addCell(right);
            doc.add(header);
            PdfPTable div = new PdfPTable(1); div.setWidthPercentage(100); div.setSpacingAfter(18);
            PdfPCell dc = new PdfPCell(new Phrase(" ")); dc.setFixedHeight(3); dc.setBackgroundColor(BLUE); dc.setBorder(Rectangle.NO_BORDER);
            div.addCell(dc); doc.add(div);
            doc.add(section("Informații Rețetă", BLUE));
            doc.add(infoRow("Pacient",    rx.getPatient().getFullName(), LGRAY, BORD));
            doc.add(infoRow("Medic",      "Dr. " + rx.getDoctor().getFullName(), Color.WHITE, BORD));
            doc.add(infoRow("Specialitate", rx.getDoctor().getSpecialty(), LGRAY, BORD));
            doc.add(infoRow("Data emiterii", rx.getIssuedAt().format(fmt), Color.WHITE, BORD));
            if (rx.getDiagnosis() != null && !rx.getDiagnosis().isBlank())
                doc.add(infoRow("Diagnostic", rx.getDiagnosis(), LGRAY, BORD));

            doc.add(section("Medicamente prescrise", BLUE));
            doc.add(contentBox(rx.getMedications(), LGRAY, BORD));

            if (rx.getInstructions() != null && !rx.getInstructions().isBlank()) {
                doc.add(section("Instrucțiuni de administrare", BLUE));
                doc.add(contentBox(rx.getInstructions(), LGRAY, BORD));
            }
            doc.add(Chunk.NEWLINE);
            PdfPTable sig = new PdfPTable(2); sig.setWidthPercentage(100); sig.setSpacingBefore(20);
            PdfPCell sc = new PdfPCell(); sc.setBorder(Rectangle.NO_BORDER);
            sc.addElement(new Phrase("Medic", FontFactory.getFont(FontFactory.HELVETICA, 9, GRAY)));
            sc.addElement(new Phrase("Dr. " + rx.getDoctor().getFullName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(15,23,42))));
            sc.addElement(new Phrase("\n_________________________", FontFactory.getFont(FontFactory.HELVETICA, 9, BORD)));
            sig.addCell(sc);
            PdfPCell sd = new PdfPCell(); sd.setBorder(Rectangle.NO_BORDER); sd.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sd.addElement(new Phrase("Data emiterii", FontFactory.getFont(FontFactory.HELVETICA, 9, GRAY)));
            sd.addElement(new Phrase(rx.getIssuedAt().format(fmt), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(15,23,42))));
            sig.addCell(sd);
            doc.add(sig);

        } catch (DocumentException e) {
            throw new ClinicException("Eroare PDF: " + e.getMessage(), e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private Paragraph section(String text, Color color) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, color));
        p.setSpacingBefore(12); p.setSpacingAfter(5); return p;
    }

    private PdfPTable infoRow(String label, String value, Color bg, Color border) throws DocumentException {
        PdfPTable t = new PdfPTable(2); t.setWidthPercentage(100); t.setWidths(new float[]{1.5f, 3f}); t.setSpacingAfter(0);
        PdfPCell lc = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(100,116,139))));
        lc.setBackgroundColor(bg); lc.setBorderColor(border); lc.setPadding(7);
        PdfPCell vc = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(15,23,42))));
        vc.setBackgroundColor(bg); vc.setBorderColor(border); vc.setPadding(7);
        t.addCell(lc); t.addCell(vc); return t;
    }

    private PdfPTable contentBox(String text, Color bg, Color border) throws DocumentException {
        PdfPTable t = new PdfPTable(1); t.setWidthPercentage(100); t.setSpacingAfter(6);
        PdfPCell c = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(15,23,42))));
        c.setBackgroundColor(bg); c.setBorderColor(border); c.setPadding(12); c.setLeading(0, 1.5f);
        t.addCell(c); return t;
    }
}
