package com.example.clinic.service;

import com.example.clinic.dto.SymptomAnalysisResult;

import java.util.List;
public interface AiService {
    String recommendSpecialty(String symptoms, List<String> availableSpecialties);
    SymptomAnalysisResult analyzeSymptoms(String symptoms);
}
