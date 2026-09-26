package com.example.clinic.controller;

import com.example.clinic.ai.AiProviderException;
import com.example.clinic.ai.MediClinicAiAgent;
import com.example.clinic.dto.AiAgentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    private final MediClinicAiAgent agent;

    public AiAgentController(MediClinicAiAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Map<String, String>> chat(@Valid @RequestBody AiAgentRequest request) {
        try {
            return ResponseEntity.ok(Map.of("message", agent.chat(request.message())));
        } catch (AiProviderException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "AI assistant is currently unavailable"));
        }
    }
}
