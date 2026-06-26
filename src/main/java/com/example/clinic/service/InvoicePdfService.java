package com.example.clinic.service;

import com.example.clinic.domain.Invoice;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.InvoiceRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


@Service
public class InvoicePdfService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", Locale.of("ro", "RO"));
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("ro", "RO"));

    private static final Color BRAND_BLUE  = new Color(13, 110, 253);
    private static final Color BRAND_DARK  = new Color(30, 41, 59);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color BORDER_GRAY = new Color(226, 232, 240);
    private static final Color TEXT_MUTED  = new Color(100, 116, 139);
    private static final Color SUCCESS     = new Color(21, 128, 61);

    private final InvoiceRepository invoiceRepository;

    public InvoicePdfService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ClinicException("Factura nu există: " + invoiceId));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 55, 55, 55, 65);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            addHeader(doc, invoice);
            addDivider(doc, BRAND_BLUE);
            addBillingSection(doc, invoice);
            addItemsTable(doc, invoice);
            addTotalSection(doc, invoice);
            addPaymentStatus(doc, invoice);
            addFooterNote(doc);

        } catch (DocumentException e) {
            throw new ClinicException("Eroare generare PDF factură: " + e.getMessage(), e);
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    private void addHeader(Document doc, Invoice invoice) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3f, 2f});
        header.setSpacingAfter(20);
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Phrase("MediClinic", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, BRAND_BLUE)));
        left.addElement(new Phrase("Platforma Smart de Sănătate", FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
        left.addElement(new Phrase("clinic@mediclinic.ro  |  +40 700 000 000", FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED)));
        header.addCell(left);
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.addElement(new Phrase("FACTURĂ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_DARK)));
        right.addElement(new Phrase("Nr. FC-" + String.format("%05d", invoice.getId()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_BLUE)));
        right.addElement(new Phrase("Data: " + invoice.getIssuedAt().format(DATE_FMT),
                FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
        header.addCell(right);

        doc.add(header);
    }

    private void addDivider(Document doc, Color color) throws DocumentException {
        PdfPTable div = new PdfPTable(1);
        div.setWidthPercentage(100);
        div.setSpacingAfter(22);
        PdfPCell c = new PdfPCell(new Phrase(" "));
        c.setFixedHeight(3);
        c.setBackgroundColor(color);
        c.setBorder(Rectangle.NO_BORDER);
        div.addCell(c);
        doc.add(div);
    }

    private void addBillingSection(Document doc, Invoice invoice) throws DocumentException {
        PdfPTable billing = new PdfPTable(2);
        billing.setWidthPercentage(100);
        billing.setWidths(new float[]{1f, 1f});
        billing.setSpacingAfter(22);

        billing.addCell(sectionCard("FACTURAT CĂTRE",
                invoice.getPatient().getFullName(),
                invoice.getPatient().getUser().getEmail()));

        billing.addCell(sectionCard("DETALII PROGRAMARE",
                invoice.getAppointment() != null
                        ? "Dr. " + invoice.getAppointment().getDoctor().getFullName()
                        : "-",
                invoice.getAppointment() != null
                        ? invoice.getAppointment().getAppointmentDate().format(FMT)
                        : "-"));

        doc.add(billing);
    }

    private PdfPCell sectionCard(String title, String line1, String line2) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(12);
        cell.addElement(new Phrase(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_MUTED)));
        cell.addElement(new Phrase("\n", FontFactory.getFont(FontFactory.HELVETICA, 4)));
        cell.addElement(new Phrase(line1, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_DARK)));
        cell.addElement(new Phrase(line2, FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED)));
        return cell;
    }

    private void addItemsTable(Document doc, Invoice invoice) throws DocumentException {
        doc.add(new Phrase("DETALII SERVICII", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_MUTED)));
        doc.add(new Phrase("\n", FontFactory.getFont(FontFactory.HELVETICA, 4)));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 1f, 1.2f, 1.2f});
        table.setSpacingBefore(6);
        table.setSpacingAfter(16);
        for (String col : new String[]{"Descriere", "Cantitate", "Preț unitar", "Total"}) {
            PdfPCell h = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            h.setBackgroundColor(BRAND_BLUE);
            h.setPadding(8);
            h.setBorderColor(BRAND_BLUE);
            table.addCell(h);
        }
        String desc = invoice.getDescription() != null ? invoice.getDescription() : "Taxă de rezervare consultație";
        String amount = "$" + invoice.getAmount().toPlainString();

        for (String val : new String[]{desc, "1", amount, amount}) {
            PdfPCell c = new PdfPCell(new Phrase(val, FontFactory.getFont(FontFactory.HELVETICA, 9, BRAND_DARK)));
            c.setBackgroundColor(LIGHT_GRAY);
            c.setPadding(8);
            c.setBorderColor(BORDER_GRAY);
            table.addCell(c);
        }

        doc.add(table);
    }

    private void addTotalSection(Document doc, Invoice invoice) throws DocumentException {
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(60);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingAfter(16);

        addTotalRow(totals, "Subtotal", "$" + invoice.getAmount().toPlainString(), false);
        addTotalRow(totals, "TVA (0%)", "$0.00", false);
        addTotalRow(totals, "TOTAL", "$" + invoice.getAmount().toPlainString(), true);

        doc.add(totals);
    }

    private void addTotalRow(PdfPTable t, String label, String value, boolean bold) {
        Font f = bold
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_DARK)
                : FontFactory.getFont(FontFactory.HELVETICA, 9, TEXT_MUTED);
        Color bg = bold ? new Color(219, 234, 254) : Color.WHITE;

        PdfPCell lc = new PdfPCell(new Phrase(label, f));
        lc.setBackgroundColor(bg); lc.setPadding(7); lc.setBorderColor(BORDER_GRAY);
        PdfPCell vc = new PdfPCell(new Phrase(value, bold
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_BLUE)
                : f));
        vc.setBackgroundColor(bg); vc.setPadding(7); vc.setBorderColor(BORDER_GRAY);
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(lc); t.addCell(vc);
    }

    private void addPaymentStatus(Document doc, Invoice invoice) throws DocumentException {
        boolean paid = "PAID".equals(invoice.getStatus());
        Color bg    = paid ? new Color(220, 252, 231) : new Color(254, 243, 199);
        Color color = paid ? SUCCESS : new Color(146, 64, 14);

        PdfPTable status = new PdfPTable(1);
        status.setWidthPercentage(100);
        status.setSpacingAfter(18);

        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(bg); c.setBorderColor(paid ? new Color(187, 247, 208) : new Color(253, 230, 138));
        c.setPadding(10);

        String label = paid ? "✓ PLĂTIT" : "⚠ ÎN AȘTEPTARE";
        c.addElement(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, color)));
        if (paid && invoice.getPaidAt() != null)
            c.addElement(new Phrase("Data plății: " + invoice.getPaidAt().format(FMT),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, color)));
        if (paid && invoice.getStripePaymentIntentId() != null)
            c.addElement(new Phrase("Ref: " + invoice.getStripePaymentIntentId(),
                    FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED)));

        status.addCell(c);
        doc.add(status);
    }

    private void addFooterNote(Document doc) throws DocumentException {
        Paragraph note = new Paragraph(
                "Această factură a fost generată automat de sistemul MediClinic. " +
                "Documentul este valabil fără semnătură și ștampilă conform legislației privind factura electronică.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, TEXT_MUTED));
        note.setAlignment(Element.ALIGN_CENTER);
        doc.add(note);
    }

    private static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
            PdfContentByte cb = writer.getDirectContent();
            cb.setColorStroke(new Color(226, 232, 240));
            cb.moveTo(55, 52); cb.lineTo(540, 52); cb.stroke();
            cb.beginText();
            cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false), 8);
            cb.setColorFill(new Color(148, 163, 184));
            cb.showTextAligned(Element.ALIGN_LEFT, "MediClinic - Platformă Smart de Sănătate", 55, 40, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Pagina " + writer.getPageNumber(), 540, 40, 0);
            cb.endText();
            } catch (Exception ignored) {}
        }
    }
}
