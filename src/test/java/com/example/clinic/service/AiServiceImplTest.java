package com.example.clinic.service;

import com.example.clinic.dto.SymptomAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceImplTest {

    private AiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiServiceImpl();
        ReflectionTestUtils.setField(service, "apiKey", "YOUR_DEEPSEEK_API_KEY_HERE");
        ReflectionTestUtils.setField(service, "apiUrl", "https://api.deepseek.com/v1/chat/completions");
        ReflectionTestUtils.setField(service, "model", "deepseek-chat");
    }

    @Test
    void analyzeSymptoms_chestPain_returnsCriticalUrgent() {
        SymptomAnalysisResult result = service.analyzeSymptoms("chest pain and shortness of breath");

        assertThat(result.getPriority()).isEqualTo("CRITICAL");
        assertThat(result.isUrgent()).isTrue();
        assertThat(result.getConfidencePercent()).isEqualTo(95);
        assertThat(result.getPriorityBadgeClass()).isEqualTo("badge bg-danger");
    }

    @Test
    void analyzeSymptoms_romanianChestPain_returnsCritical() {
        SymptomAnalysisResult result = service.analyzeSymptoms("durere piept severa");

        assertThat(result.getPriority()).isEqualTo("CRITICAL");
        assertThat(result.isUrgent()).isTrue();
        assertThat(result.getSuggestedSpecialty()).isEqualTo("cardiolog");
    }

    @Test
    void analyzeSymptoms_diacriticsNormalized_detectedAsCritical() {
        SymptomAnalysisResult result = service.analyzeSymptoms("durere în piept");

        assertThat(result.getPriority()).isEqualTo("CRITICAL");
    }

    @Test
    void analyzeSymptoms_stroke_returnsCriticalNeurolog() {
        SymptomAnalysisResult result = service.analyzeSymptoms("stroke and face drooping");

        assertThat(result.getPriority()).isEqualTo("CRITICAL");
        assertThat(result.isUrgent()).isTrue();
        assertThat(result.getSuggestedSpecialty()).isEqualTo("neurolog");
    }

    @Test
    void analyzeSymptoms_convulsii_returnsCritical() {
        SymptomAnalysisResult result = service.analyzeSymptoms("convulsii repetate si epileptic");

        assertThat(result.getPriority()).isEqualTo("CRITICAL");
        assertThat(result.isUrgent()).isTrue();
    }

    @Test
    void analyzeSymptoms_lossOfConsciousness_returnsCritical() {
        SymptomAnalysisResult result = service.analyzeSymptoms("patient is unconscious and collapsed");

        assertThat(result.getPriority()).isEqualTo("CRITICAL");
        assertThat(result.isUrgent()).isTrue();
    }

    @Test
    void analyzeSymptoms_severeHeadache_returnsHighUrgent() {
        SymptomAnalysisResult result = service.analyzeSymptoms("severe headache that came on suddenly");

        assertThat(result.getPriority()).isEqualTo("HIGH");
        assertThat(result.isUrgent()).isTrue();
        assertThat(result.getConfidencePercent()).isEqualTo(85);
        assertThat(result.getPriorityBadgeClass()).isEqualTo("badge bg-warning text-dark");
    }

    @Test
    void analyzeSymptoms_fracture_returnsHighOrtopedie() {
        SymptomAnalysisResult result = service.analyzeSymptoms("fracture of left arm, cannot walk");

        assertThat(result.getPriority()).isEqualTo("HIGH");
        assertThat(result.getSuggestedSpecialty()).isEqualTo("ortopedie");
    }

    @Test
    void analyzeSymptoms_highFever_returnsHigh() {
        SymptomAnalysisResult result = service.analyzeSymptoms("high fever 40 degrees since this morning");

        assertThat(result.getPriority()).isEqualTo("HIGH");
        assertThat(result.isUrgent()).isTrue();
    }

    @Test
    void analyzeSymptoms_regularHeadache_returnsMediumNotUrgent() {
        SymptomAnalysisResult result = service.analyzeSymptoms("headache since this morning");

        assertThat(result.getPriority()).isEqualTo("MEDIUM");
        assertThat(result.isUrgent()).isFalse();
        assertThat(result.getConfidencePercent()).isEqualTo(70);
        assertThat(result.getPriorityBadgeClass()).isEqualTo("badge bg-info text-dark");
    }

    @Test
    void analyzeSymptoms_fever_returnsMedium() {
        SymptomAnalysisResult result = service.analyzeSymptoms("I have a fever since yesterday");

        assertThat(result.getPriority()).isEqualTo("MEDIUM");
        assertThat(result.isUrgent()).isFalse();
    }

    @Test
    void analyzeSymptoms_pain_returnsMedium() {
        SymptomAnalysisResult result = service.analyzeSymptoms("mild back pain for two days");

        assertThat(result.getPriority()).isEqualTo("MEDIUM");
        assertThat(result.isUrgent()).isFalse();
    }

    @Test
    void analyzeSymptoms_cough_returnsMediumPneumolog() {
        SymptomAnalysisResult result = service.analyzeSymptoms("persistent cough for a week");

        assertThat(result.getPriority()).isEqualTo("MEDIUM");
        assertThat(result.getSuggestedSpecialty()).isEqualTo("pneumolog");
    }

    @Test
    void analyzeSymptoms_palpitations_returnsMediumCardiolog() {
        SymptomAnalysisResult result = service.analyzeSymptoms("palpitations and heart racing");

        assertThat(result.getPriority()).isEqualTo("MEDIUM");
        assertThat(result.getSuggestedSpecialty()).isEqualTo("cardiolog");
    }

    @Test
    void analyzeSymptoms_routineCheckup_returnsLow() {
        SymptomAnalysisResult result = service.analyzeSymptoms("routine annual checkup");

        assertThat(result.getPriority()).isEqualTo("LOW");
        assertThat(result.isUrgent()).isFalse();
        assertThat(result.getConfidencePercent()).isEqualTo(60);
        assertThat(result.getPriorityBadgeClass()).isEqualTo("badge bg-success");
    }

    @Test
    void analyzeSymptoms_null_returnsLow() {
        SymptomAnalysisResult result = service.analyzeSymptoms(null);

        assertThat(result.getPriority()).isEqualTo("LOW");
        assertThat(result.isUrgent()).isFalse();
    }

    @Test
    void analyzeSymptoms_blank_returnsLow() {
        SymptomAnalysisResult result = service.analyzeSymptoms("   ");

        assertThat(result.getPriority()).isEqualTo("LOW");
        assertThat(result.isUrgent()).isFalse();
    }

    @Test
    void analyzeSymptoms_empty_returnsLow() {
        SymptomAnalysisResult result = service.analyzeSymptoms("");

        assertThat(result.getPriority()).isEqualTo("LOW");
        assertThat(result.isUrgent()).isFalse();
    }

    @Test
    void recommendSpecialty_blankSymptoms_returnsDefault() {
        String specialty = service.recommendSpecialty("", List.of());

        assertThat(specialty).isEqualTo("medic_general");
    }

    @Test
    void recommendSpecialty_nullSymptoms_returnsDefault() {
        String specialty = service.recommendSpecialty(null, List.of());

        assertThat(specialty).isEqualTo("medic_general");
    }

    @Test
    void recommendSpecialty_chestSymptoms_returnsCardiologie() {
        String specialty = service.recommendSpecialty("durere piept palpitati cardio", List.of());

        assertThat(specialty).isEqualTo("Cardiologie");
    }

    @Test
    void recommendSpecialty_coughSymptoms_returnsPneumologie() {
        String specialty = service.recommendSpecialty("persistent cough and breathing difficulty", List.of());

        assertThat(specialty).isEqualTo("Pneumologie");
    }

    @Test
    void recommendSpecialty_skinSymptoms_returnsDermatologie() {
        String specialty = service.recommendSpecialty("skin rash and acnee all over", List.of());

        assertThat(specialty).isEqualTo("Dermatologie");
    }

    @Test
    void recommendSpecialty_stomachSymptoms_returnsGastroenterologie() {
        String specialty = service.recommendSpecialty("stomac durere gastro varsatur", List.of());

        assertThat(specialty).isEqualTo("Gastroenterologie");
    }
}
