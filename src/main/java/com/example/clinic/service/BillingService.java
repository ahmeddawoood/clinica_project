package com.example.clinic.service;

import com.example.clinic.domain.Invoice;
import com.example.clinic.dto.InvoiceForm;

import java.math.BigDecimal;
import java.util.List;

public interface BillingService {
    List<Invoice> getAllInvoices();
    List<Invoice> getInvoicesByPatientEmail(String email);
    Invoice getInvoiceById(Long id);
    void createInvoice(InvoiceForm form);
    void markAsPaid(Long invoiceId);
    void payByPatient(Long invoiceId, String paymentMethod);
    void cancelInvoice(Long invoiceId);
    BigDecimal getTotalPaid();
    BigDecimal getTotalPending();
    long countByStatus(String status);
}
