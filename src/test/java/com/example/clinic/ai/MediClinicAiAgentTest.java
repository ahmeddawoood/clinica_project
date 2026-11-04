package com.example.clinic.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MediClinicAiAgentTest {

    private final AiProvider provider = mock(AiProvider.class);
    private final AiToolRegistry registry = mock(AiToolRegistry.class);
    private final MediClinicAiAgent agent = new MediClinicAiAgent(provider, registry);

    @AfterEach
    void resetMocks() {
        reset(provider, registry);
    }

    @Test
    void delegatesNormalizedMessageAndAuthorizedToolsToProvider() {
        Object tool = new Object();
        when(registry.toolsForCurrentUser()).thenReturn(List.of(tool));
        when(provider.chat(eq(AiGuardrails.SYSTEM_PROMPT), eq("hello"), eq(List.of(tool))))
                .thenReturn("response");

        assertEquals("response", agent.chat("  hello  "));
        verify(provider).chat(AiGuardrails.SYSTEM_PROMPT, "hello", List.of(tool));
    }

    @Test
    void rejectsBlankMessages() {
        assertThrows(IllegalArgumentException.class, () -> agent.chat("  "));
        verifyNoInteractions(provider, registry);
    }

    @Test
    void rejectsMessagesLongerThanTheApplicationLimit() {
        String message = "x".repeat(4001);

        assertThrows(IllegalArgumentException.class, () -> agent.chat(message));
        verifyNoInteractions(provider, registry);
    }
}
