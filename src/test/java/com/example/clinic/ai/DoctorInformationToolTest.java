package com.example.clinic.ai;

import com.example.clinic.domain.Doctor;
import com.example.clinic.service.DoctorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DoctorInformationToolTest {

    private final DoctorService doctorService = mock(DoctorService.class);
    private final DoctorInformationTool tool = new DoctorInformationTool(doctorService);

    @Test
    void returnsDoctorsMatchingSpecialty() {
        Doctor cardiologist = doctor("Cardio", "Cardiologist");
        Doctor dentist = doctor("Dentist", "Dentist");
        when(doctorService.getAllDoctors()).thenReturn(List.of(cardiologist, dentist));

        var result = tool.findDoctorsBySpecialty("cardio");

        assertEquals(1, result.size());
        assertEquals("Cardiologist", result.getFirst().specialty());
    }

    @Test
    void blankSpecialtyDoesNotQueryDoctors() {
        var result = tool.findDoctorsBySpecialty("  ");

        assertEquals(List.of(), result);
    }

    private static Doctor doctor(String firstName, String specialty) {
        Doctor doctor = new Doctor();
        doctor.setFirstName(firstName);
        doctor.setSpecialty(specialty);
        return doctor;
    }
}
