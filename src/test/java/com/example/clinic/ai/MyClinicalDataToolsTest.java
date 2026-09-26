package com.example.clinic.ai;

import com.example.clinic.domain.MedicalRecord;
import com.example.clinic.domain.Prescription;
import com.example.clinic.service.MedicalRecordService;
import com.example.clinic.service.PrescriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyClinicalDataToolsTest {

    @Mock
    PrescriptionService prescriptionService;

    @Mock
    MedicalRecordService medicalRecordService;

    @Mock
    Prescription prescription;

    @Mock
    MedicalRecord medicalRecord;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prescriptionsUseAuthenticatedPatientIdentity() {
        authenticatePatient();
        when(prescriptionService.getPrescriptionsByPatientEmail("patient@example.com"))
                .thenReturn(List.of(prescription));

        var result = new MyPrescriptionsTool(prescriptionService).getMyPrescriptions();

        assertEquals(1, result.size());
        verify(prescriptionService).getPrescriptionsByPatientEmail("patient@example.com");
    }

    @Test
    void medicalHistoryUsesAuthenticatedPatientIdentity() {
        authenticatePatient();
        when(medicalRecordService.getRecordsByPatientEmail("patient@example.com"))
                .thenReturn(List.of(medicalRecord));

        var result = new MyMedicalHistoryTool(medicalRecordService).getMyMedicalHistory();

        assertEquals(1, result.size());
        verify(medicalRecordService).getRecordsByPatientEmail("patient@example.com");
    }

    @Test
    void clinicalDataToolsRejectUnauthenticatedAccess() {
        assertThrows(IllegalStateException.class,
                () -> new MyPrescriptionsTool(prescriptionService).getMyPrescriptions());
        assertThrows(IllegalStateException.class,
                () -> new MyMedicalHistoryTool(medicalRecordService).getMyMedicalHistory());
    }

    private void authenticatePatient() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("patient@example.com", "N/A", List.of()));
    }
}
