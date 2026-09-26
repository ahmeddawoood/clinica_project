package com.example.clinic.service;

import com.example.clinic.dto.SymptomAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Ollama-backed implementation of the clinic AI contract.
 *
 * This is intentionally kept behind AiService so existing application code
 * does not become coupled to the LLM provider. Tool calling and conversation
 * memory will be added above this provider in the agent layer.
 */
@Service
@Primary
public class OllamaAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiService.class);

    private static final String SYSTEM_PROMPT = """
            You are MediClinic's clinical assistant.
            You support doctors and patients with information and triage guidance.
            Do not claim to diagnose a patient or prescribe treatment.
            For urgent symptoms, tell the user to seek immediate medical care.
            When asked to select a specialty, return only one specialty from the supplied list.
            """;

    private final ChatClient chatClient;
    private final AiService fallback;
    private final boolean enabled;

    public OllamaAiService(
            ChatClient.Builder chatClientBuilder,
            AiServiceImpl fallback,
            @Value("${app.ai.ollama.enabled:true}") boolean enabled) {
        this.chatClient = chatClientBuilder.build();
        this.fallback = fallback;
        this.enabled = enabled;
    }

    @Override
    public String recommendSpecialty(String symptoms, List<String> availableSpecialties) {
        if (!enabled || symptoms == null || symptoms.isBlank()) {
            return fallback.recommendSpecialty(symptoms, availableSpecialties);
        }

        try {
            String specialties = String.join(", ", availableSpecialties == null ? List.of() : availableSpecialties);
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("""
                            Select the most appropriate clinic specialty for these symptoms.
                            Available specialties: %s
                            Symptoms: %s
                            Return only the specialty name.
                            """.formatted(specialties, symptoms.trim()))
                    .call()
                    .content();

            String mapped = mapSpecialty(response, availableSpecialties);
            if (mapped != null) {
                return mapped;
            }

            log.warn("Ollama returned an unmapped specialty: {}", response);
        } catch (Exception ex) {
            log.warn("Ollama request failed, using existing fallback: {}", ex.getMessage());
        }

        return fallback.recommendSpecialty(symptoms, availableSpecialties);
    }

    @Override
    public SymptomAnalysisResult analyzeSymptoms(String symptoms) {
        // Keep the existing deterministic safety classifier until the agent
        // has structured-output validation and medical guardrails in place.
        return fallback.analyzeSymptoms(symptoms);
    }

    private String mapSpecialty(String response, List<String> availableSpecialties) {
        if (response == null || response.isBlank()) return null;

        String normalized = response.trim().toLowerCase(Locale.ROOT);
        if (availableSpecialties != null) {
            for (String specialty : availableSpecialties) {
                if (specialty != null && normalized.contains(specialty.trim().toLowerCase(Locale.ROOT))) {
                    return specialty;
                }
            }
        }

        if (normalized.contains("cardio")) return find("Cardiologie", availableSpecialties);
        if (normalized.contains("dermat")) return find("Dermatologie", availableSpecialties);
        if (normalized.contains("neuro")) return find("Neurologie", availableSpecialties);
        if (normalized.contains("pediatr")) return find("Pediatrie", availableSpecialties);
        if (normalized.contains("ortho")) return find("Ortopedie", availableSpecialties);
        if (normalized.contains("pulmon") || normalized.contains("pneumo")) return find("Pneumologie", availableSpecialties);
        if (normalized.contains("gastro")) return find("Gastroenterologie", availableSpecialties);
        if (normalized.contains("ophthal") || normalized.contains("oftalm")) return find("Oftalmologie", availableSpecialties);
        if (normalized.contains("psychi") || normalized.contains("psih")) return find("Psihiatrie", availableSpecialties);
        if (normalized.contains("endocrin")) return find("Endocrinologie", availableSpecialties);
        if (normalized.contains("urol")) return find("Urologie", availableSpecialties);
        if (normalized.contains("general")) return find("medic_general", availableSpecialties);
        return null;
    }

    private String find(String expected, List<String> availableSpecialties) {
        if (availableSpecialties == null) return null;
        return availableSpecialties.stream()
                .filter(s -> s != null && s.equalsIgnoreCase(expected))
                .findFirst()
                .orElse(null);
    }
}
