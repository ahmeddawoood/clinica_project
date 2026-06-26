package com.example.clinic.service;

import com.example.clinic.dto.SymptomAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);
    private static final String DEFAULT_SPECIALTY = "medic_general";
    private static final Map<String, String> AI_TO_DB_SPECIALTY = Map.ofEntries(
            Map.entry("cardiology",      "Cardiologie"),
            Map.entry("dermatology",     "Dermatologie"),
            Map.entry("neurology",       "Neurologie"),
            Map.entry("general",         DEFAULT_SPECIALTY),
            Map.entry("pediatrics",      "Pediatrie"),
            Map.entry("orthopedics",     "Ortopedie"),
            Map.entry("pulmonology",     "Pneumologie"),
            Map.entry("gastroenterology","Gastroenterologie"),
            Map.entry("ophthalmology",   "Oftalmologie"),
            Map.entry("psychiatry",      "Psihiatrie"),
            Map.entry("endocrinology",   "Endocrinologie"),
            Map.entry("urology",         "Urologie")
    );
    private static final String SYSTEM_PROMPT = """
            You are a medical triage assistant.
            Return exactly one word from this list:
            cardiology, dermatology, neurology, general, pediatrics,
            orthopedics, pulmonology, gastroenterology, ophthalmology,
            psychiatry, endocrinology, urology.
            No explanation. No punctuation. Lowercase only.
            """;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String recommendSpecialty(String symptoms, List<String> availableSpecialties) {
        String cleanSymptoms = symptoms == null ? "" : symptoms.trim();
        if (cleanSymptoms.isBlank()) {
            log.warn("Empty symptoms received, defaulting to {}", DEFAULT_SPECIALTY);
            return DEFAULT_SPECIALTY;
        }

        if (isAiConfigured()) {
            String aiRawSpecialty = callAi(cleanSymptoms);
            String mapped = mapAiToDbSpecialty(aiRawSpecialty);
            if (mapped != null) {
                log.info("AI mapped specialty: symptoms='{}' ai='{}' -> db='{}'", cleanSymptoms, aiRawSpecialty, mapped);
                return mapped;
            }

            log.warn("AI mapping failed. ai='{}' symptoms='{}'. Falling back to keyword rules.", aiRawSpecialty, cleanSymptoms);
        } else {
            log.warn("DeepSeek API key not configured, using keyword fallback only");
        }

        String fallback = mapByKeywords(cleanSymptoms);
        log.info("Keyword fallback: symptoms='{}' -> db='{}'", cleanSymptoms, fallback);
        return fallback;
    }

    @Override
    public SymptomAnalysisResult analyzeSymptoms(String symptoms) {
        if (symptoms == null || symptoms.isBlank()) {
            return new SymptomAnalysisResult("LOW", false, DEFAULT_SPECIALTY, "");
        }
        String n = normalize(symptoms);
        if (containsAny(n,
                "chest pain", "chest tightness", "heart attack", "myocardial",
                "durere piept", "durere in piept", "infarct", "atac cord",
                "cant breathe", "cannot breathe", "not breathing", "stop breathing",
                "nu respir", "nu pot respira", "stop respiratiei",
                "shortness of breath", "respiratory arrest",
                "loss of consciousness", "unconscious", "fainted", "collapsed",
                "pierdut cunostinta", "lesin grav", "cazut inconstient",
                "stroke", "atac cerebral", "paralysis", "paralizie", "face drooping",
                "severe bleeding", "sangerare severa", "hemoragie",
                "seizure", "convulsions", "convulsii", "epileptic")) {
            String specialty = pickSpecialtyCritical(n);
            String reason = buildReason(n);
            log.warn("CRITICAL emergency detected: symptoms='{}' specialty='{}'", symptoms, specialty);
            return new SymptomAnalysisResult("CRITICAL", true, specialty, reason);
        }
        if (containsAny(n,
                "severe headache", "sudden headache", "worst headache", "thunderclap",
                "durere severa cap", "cefalee severa", "migrena severa",
                "breathing difficulty", "difficulty breathing", "shortness",
                "dificultati respiratie", "respiratie dificila", "greu de respirat",
                "high fever", "fever 39", "fever 40", "febra 39", "febra 40", "febra mare",
                "fracture", "broken bone", "fractura", "os rupt",
                "severe pain", "extreme pain", "durere extrema", "durere insuportabila",
                "can not walk", "cannot walk", "nu pot merge",
                "repeated vomiting", "varsaturi repetate", "vomiting blood", "varsat sange",
                "severe dizziness", "ameteli severe", "vertigo sever")) {
            String specialty = pickSpecialtyHigh(n);
            String reason = buildReason(n);
            log.warn("HIGH urgency detected: symptoms='{}' specialty='{}'", symptoms, specialty);
            return new SymptomAnalysisResult("HIGH", true, specialty, reason);
        }
        if (containsAny(n,
                "headache", "cap doare", "durere de cap",
                "fever", "febra", "temperatura",
                "cough", "tuse", "tuse persistenta",
                "infection", "infectie",
                "nausea", "greata", "vomiting", "voma",
                "dizziness", "ameteala",
                "fatigue", "oboseala", "epuizare",
                "swelling", "umflatura", "edem",
                "rash", "eruptie", "urticarie",
                "pain", "durere", "dureri",
                "palpitations", "palpitati", "heart racing",
                "burn", "arsura")) {
            String specialty = pickSpecialtyMedium(n);
            return new SymptomAnalysisResult("MEDIUM", false, specialty, "");
        }
        return new SymptomAnalysisResult("LOW", false, DEFAULT_SPECIALTY, "");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String pickSpecialtyCritical(String n) {
        if (containsAny(n, "chest", "piept", "infarct", "heart", "cord", "cardiac")) return "cardiolog";
        if (containsAny(n, "breath", "respir", "pulmonar"))                           return "pneumolog";
        if (containsAny(n, "stroke", "cerebral", "paralizie", "paralysis", "convul", "seizure")) return "neurolog";
        return "urgente";
    }

    private String pickSpecialtyHigh(String n) {
        if (containsAny(n, "head", "cap", "cefalee", "migrain"))          return "neurolog";
        if (containsAny(n, "breath", "respir", "pulmonar"))                return "pneumolog";
        if (containsAny(n, "chest", "piept", "heart", "cord"))             return "cardiolog";
        if (containsAny(n, "fractur", "bone", "os rupt", "ortoped"))       return "ortopedie";
        return DEFAULT_SPECIALTY;
    }

    private String pickSpecialtyMedium(String n) {
        if (containsAny(n, "piept", "chest", "heart", "cord", "palpitat")) return "cardiolog";
        if (containsAny(n, "piele", "skin", "rash", "eruptie", "urticar")) return "dermatolog";
        if (containsAny(n, "cap", "head", "cefalee", "migrain", "neuro"))  return "neurolog";
        if (containsAny(n, "tuse", "cough", "respir", "pulmonar"))         return "pneumolog";
        return DEFAULT_SPECIALTY;
    }

    private String buildReason(String n) {
        if (containsAny(n, "chest pain", "chest", "piept", "infarct"))          return "Durere în piept detectată - risc cardiac";
        if (containsAny(n, "breath", "respir", "shortness"))                     return "Dificultate respiratorie detectată";
        if (containsAny(n, "severe headache", "thunderclap", "cefalee severa"))  return "Cefalee severă bruscă detectată";
        if (containsAny(n, "stroke", "cerebral", "paralizie"))                   return "Simptome neurologice grave detectate";
        if (containsAny(n, "unconscious", "cunostinta", "collapsed"))            return "Pierdere conștiință detectată";
        if (containsAny(n, "bleed", "sanger", "hemoragie"))                      return "Sângerare severă detectată";
        if (containsAny(n, "seizure", "convuls"))                                return "Convulsii detectate";
        return "Simptome urgente detectate";
    }

    private boolean isAiConfigured() {
        return apiKey != null
                && !apiKey.isBlank()
                && !"YOUR_DEEPSEEK_API_KEY_HERE".equals(apiKey)
                && apiUrl != null
                && !apiUrl.isBlank();
    }

    private String callAi(String symptoms) {
        try {
            String requestBody = buildJson(symptoms);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("DeepSeek raw response: {}", response.body());

            if (response.statusCode() != 200) {
                log.error("DeepSeek API error: status={} body={}", response.statusCode(), response.body());
                return null;
            }

            return extractContent(response.body());
        } catch (Exception e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildJson(String userContent) {
        return "{"
                + "\"model\":" + quote(model) + ","
                + "\"max_tokens\":6,"
                + "\"temperature\":0,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + quote(SYSTEM_PROMPT) + "},"
                + "{\"role\":\"user\",\"content\":" + quote(userContent) + "}"
                + "]"
                + "}";
    }

    private String extractContent(String json) {
        try {
            String content = extractLastJsonStringValue(json, "\"content\":");
            String normalized = normalize(content);
            log.info("DeepSeek extracted content: raw='{}' normalized='{}'", content, normalized);
            return normalized;
        } catch (Exception e) {
            log.error("Failed to parse DeepSeek response: {}", e.getMessage());
            return null;
        }
    }

    private String extractLastJsonStringValue(String json, String key) {
        if (json == null || json.isBlank()) return "";
        int idx = json.lastIndexOf(key);
        if (idx < 0) return "";

        int startQuote = json.indexOf('"', idx + key.length());
        if (startQuote < 0) return "";

        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case '\\', '"' -> value.append(ch);
                    default -> value.append(ch);
                }
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                break;
            }
            value.append(ch);
        }
        return value.toString();
    }

    private String mapAiToDbSpecialty(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return null;

        String[] tokens = aiResponse.split("\\s+");
        String firstWord = tokens.length == 0 ? "" : tokens[0];
        String mapped = AI_TO_DB_SPECIALTY.get(firstWord);
        if (mapped != null) return mapped;
        if (firstWord.contains("cardio"))   return "Cardiologie";
        if (firstWord.contains("derm"))     return "Dermatologie";
        if (firstWord.contains("neuro"))    return "Neurologie";
        if (firstWord.contains("pediatr"))  return "Pediatrie";
        if (firstWord.contains("ortho") || firstWord.contains("ortoped")) return "Ortopedie";
        if (firstWord.contains("pulmon") || firstWord.contains("pneumo")) return "Pneumologie";
        if (firstWord.contains("gastro"))   return "Gastroenterologie";
        if (firstWord.contains("ophthal") || firstWord.contains("oftalm")) return "Oftalmologie";
        if (firstWord.contains("psychi") || firstWord.contains("psih"))   return "Psihiatrie";
        if (firstWord.contains("endocrin")) return "Endocrinologie";
        if (firstWord.contains("urol"))     return "Urologie";
        if (firstWord.contains("general"))  return DEFAULT_SPECIALTY;
        return null;
    }

    private String mapByKeywords(String symptoms) {
        String n = normalize(symptoms);
        if (containsAny(n, "piept", "chest", "inima", "cord", "palpitat", "cardio")) return "Cardiologie";
        if (containsAny(n, "piele", "skin", "rash", "eruptie", "acnee", "derm"))    return "Dermatologie";
        if (containsAny(n, "cap", "head", "cefalee", "migrain", "neuro", "vertij")) return "Neurologie";
        if (containsAny(n, "tuse", "cough", "respir", "pulmon", "pneumon"))         return "Pneumologie";
        if (containsAny(n, "copil", "child", "pediatr", "sugar", "febra copil"))    return "Pediatrie";
        if (containsAny(n, "os", "fractur", "articulat", "ortoped", "genunchi"))    return "Ortopedie";
        if (containsAny(n, "stomac", "abdomen", "digestiv", "gastro", "varsatur"))  return "Gastroenterologie";
        if (containsAny(n, "ochi", "vedere", "oftalm", "vision"))                   return "Oftalmologie";
        if (containsAny(n, "anxietate", "depresie", "psih", "somn", "stress"))      return "Psihiatrie";
        if (containsAny(n, "diabet", "tiroid", "hormoni", "endocrin"))              return "Endocrinologie";
        if (containsAny(n, "urina", "rinichi", "urol", "bladder"))                  return "Urologie";
        return DEFAULT_SPECIALTY;
    }

    private String normalize(String value) {
        if (value == null) return "";

        String ascii = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        return ascii
                .replaceAll("[^a-z\\s_]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String quote(String value) {
        String v = value == null ? "" : value;
        return "\"" + v
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
