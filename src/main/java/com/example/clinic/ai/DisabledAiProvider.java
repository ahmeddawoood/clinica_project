package com.example.clinic.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.ollama.enabled", havingValue = "false")
public class DisabledAiProvider implements AiProvider {

    @Override
    public String chat(String systemPrompt, String userMessage, List<Object> tools) {
        throw new AiProviderException("AI provider is disabled");
    }
}
