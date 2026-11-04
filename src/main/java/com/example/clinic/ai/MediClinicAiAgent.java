package com.example.clinic.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MediClinicAiAgent {

    private static final String SYSTEM_PROMPT = """
            You are the MediClinic assistant.
            You can help authenticated patients with clinic information and their own data.
            Use tools when the user's question requires clinic data.
            Never ask for or accept a patient ID to access another patient's data.
            Never expose data belonging to another user.
            Do not diagnose, prescribe medication, or change medical records.
            For urgent symptoms, advise the user to seek immediate medical care.
            If a tool fails, explain that the requested clinic information is currently unavailable.
            Keep answers concise and factual.
            """;

    private final ChatClient chatClient;
    private final MyAppointmentsTool myAppointmentsTool;

    public MediClinicAiAgent(ChatClient.Builder chatClientBuilder,
                             MyAppointmentsTool myAppointmentsTool) {
        this.chatClient = chatClientBuilder.build();
        this.myAppointmentsTool = myAppointmentsTool;
    }

    public String chat(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be blank");
        }

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message.trim())
                .tools(myAppointmentsTool)
                .call()
                .content();
    }
}
