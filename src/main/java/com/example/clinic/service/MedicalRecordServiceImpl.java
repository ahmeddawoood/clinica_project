package com.example.clinic.service;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.MedicalRecord;
import com.example.clinic.domain.Patient;
import com.example.clinic.dto.MedicalRecordForm;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.exception.DoctorNotFoundException;
import com.example.clinic.exception.PatientNotFoundException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.MedicalRecordRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordRepository recordRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    public MedicalRecordServiceImpl(MedicalRecordRepository recordRepository,
                                     PatientRepository patientRepository,
                                     DoctorRepository doctorRepository,
                                     UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<MedicalRecord> getRecordsByPatientEmail(String email) {
        Patient patient = getPatientByEmail(email);
        return recordRepository.findByPatientOrderByCreatedAtDesc(patient);
    }

    @Override
    public List<MedicalRecord> getRecordsByDoctorEmail(String email) {
        Doctor doctor = getDoctorByEmail(email);
        return recordRepository.findByDoctorOrderByCreatedAtDesc(doctor);
    }

    @Override
    public MedicalRecord getRecordById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new ClinicException("Fișa medicală nu există: " + id));
    }

    @Override
    @Transactional
    public void addRecord(MedicalRecordForm form, String doctorEmail) {
        Doctor doctor = getDoctorByEmail(doctorEmail);
        Patient patient = patientRepository.findById(form.getPatientId())
                .orElseThrow(() -> new PatientNotFoundException("id=" + form.getPatientId()));

        MedicalRecord record = new MedicalRecord();
        record.setPatient(patient);
        record.setDoctor(doctor);
        record.setTitle(form.getTitle());
        record.setSymptoms(form.getSymptoms());
        record.setDiagnosis(form.getDiagnosis());
        record.setTreatment(form.getTreatment());
        record.setNotes(form.getNotes());

        recordRepository.save(record);
    }

    @Override
    @Transactional
    public void deleteRecord(Long id) {
        recordRepository.deleteById(id);
    }

    private Patient getPatientByEmail(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new PatientNotFoundException(email));
        return patientRepository.findByUser(user)
                .orElseThrow(() -> new PatientNotFoundException(email));
    }

    private Doctor getDoctorByEmail(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DoctorNotFoundException(email));
        return doctorRepository.findByUser(user)
                .orElseThrow(() -> new DoctorNotFoundException(email));
    }
}
