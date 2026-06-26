package com.example.clinic.service;

import com.example.clinic.domain.Appointment;
import com.example.clinic.domain.Invoice;
import com.example.clinic.domain.Patient;
import com.example.clinic.dto.InvoiceForm;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.PatientNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.InvoiceRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillingServiceImpl implements BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    public BillingServiceImpl(InvoiceRepository invoiceRepository,
                               PatientRepository patientRepository,
                               AppointmentRepository appointmentRepository,
                               UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    @Override
    public List<Invoice> getInvoicesByPatientEmail(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new PatientNotFoundException(email));
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new PatientNotFoundException(email));
        return invoiceRepository.findByPatientOrderByIssuedAtDesc(patient);
    }

    @Override
    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ClinicException("Factura nu există: " + id));
    }

    @Override
    @Transactional
    public void createInvoice(InvoiceForm form) {
        Patient patient = patientRepository.findById(form.getPatientId())
                .orElseThrow(() -> new PatientNotFoundException("id=" + form.getPatientId()));

        Invoice invoice = new Invoice();
        invoice.setPatient(patient);
        invoice.setAmount(form.getAmount());
        invoice.setDescription(form.getDescription());
        invoice.setStatus("PENDING");

        if (form.getAppointmentId() != null) {
            Appointment appointment = appointmentRepository.findById(form.getAppointmentId()).orElse(null);
            invoice.setAppointment(appointment);
        }

        invoiceRepository.save(invoice);
        log.info("Invoice created: patient={} amount={} RON",
                patient.getFullName(), form.getAmount());
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public void markAsPaid(Long invoiceId) {
        Invoice invoice = getInvoiceById(invoiceId);
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
        log.info("Invoice paid: id={} patient={} amount={} RON",
                invoiceId, invoice.getPatient().getFullName(), invoice.getAmount());
    }

    @Override
    @Transactional
    public void payByPatient(Long invoiceId, String paymentMethod) {
        Invoice invoice = getInvoiceById(invoiceId);
        if (!"PENDING".equals(invoice.getStatus())) {
            throw new ClinicException("Factura nu mai poate fi plătită.");
        }
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaymentMethod(paymentMethod);
        invoiceRepository.save(invoice);
        log.info("Invoice paid by patient: id={} method={} amount={} RON",
                invoiceId, paymentMethod, invoice.getAmount());
    }

    @Override
    @Transactional
    public void cancelInvoice(Long invoiceId) {
        Invoice invoice = getInvoiceById(invoiceId);
        invoice.setStatus("CANCELLED");
        invoiceRepository.save(invoice);
        log.info("Invoice cancelled: id={}", invoiceId);
    }

    @Override
    public BigDecimal getTotalPaid() {
        return invoiceRepository.sumPaidAmount();
    }

    @Override
    public BigDecimal getTotalPending() {
        return invoiceRepository.sumPendingAmount();
    }

    @Override
    public long countByStatus(String status) {
        return invoiceRepository.countByStatus(status);
    }
}
