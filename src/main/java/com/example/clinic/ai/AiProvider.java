package com.example.clinic.ai;

import java.util.List;

public interface AiProvider {

    String chat(String systemPrompt, String userMessage, List<Object> tools);
}
