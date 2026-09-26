package com.example.clinic.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ai.ollama.enabled", havingValue = "true", matchIfMissing = true)
public class OllamaProvider implements AiProvider {

    private final ChatClient chatClient;

    public OllamaProvider(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<Object> tools) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .tools(tools.toArray())
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            throw new AiProviderException("AI provider is currently unavailable", ex);
        }
    }
}
