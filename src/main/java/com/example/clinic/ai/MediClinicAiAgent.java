package com.example.clinic.ai;

import org.springframework.stereotype.Service;

@Service
public class MediClinicAiAgent {

    private final AiProvider aiProvider;
    private final AiToolRegistry toolRegistry;

    public MediClinicAiAgent(AiProvider aiProvider, AiToolRegistry toolRegistry) {
        this.aiProvider = aiProvider;
        this.toolRegistry = toolRegistry;
    }

    public String chat(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }

        String normalizedMessage = message.trim();
        if (normalizedMessage.length() > 4000) {
            throw new IllegalArgumentException("Message is too long");
        }

        String response = aiProvider.chat(
                AiGuardrails.SYSTEM_PROMPT,
                normalizedMessage,
                toolRegistry.toolsForCurrentUser());
        if (response == null || response.isBlank()) {
            throw new AiProviderException("AI provider returned an empty response");
        }
        return response;
    }
}
