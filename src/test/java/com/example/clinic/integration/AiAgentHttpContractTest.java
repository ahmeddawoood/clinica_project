package com.example.clinic.integration;

import com.example.clinic.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.ai.ollama.enabled=false")
@ActiveProfiles("test")
class AiAgentHttpContractTest {
    @Autowired WebApplicationContext context;
    @MockitoBean AiProvider provider;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"   \"}", "{broken"})
    @WithMockUser(roles = "PATIENT")
    void invalidInputReturns400WithoutInvokingProvider(String body) throws Exception {
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
        verifyNoInteractions(provider);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void rejectsOversizedMessageBeforeProvider() throws Exception {
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void acceptsMessageAtLimitWithFourAuthorizedTools() throws Exception {
        when(provider.chat(anyString(), anyString(), any())).thenReturn("ok");
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"" + "x".repeat(4000) + "\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"message\":\"ok\"}"));
        verify(provider).chat(eq(AiGuardrails.SYSTEM_PROMPT), eq("x".repeat(4000)),
                argThat(tools -> tools.size() == 4));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @WithMockUser(roles = "PATIENT")
    void emptyProviderResponseReturns503(String reply) throws Exception {
        when(provider.chat(anyString(), anyString(), any())).thenReturn(reply);
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"message\":\"AI assistant is currently unavailable\"}"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void unavailableProviderReturns503WithoutInternalDetails() throws Exception {
        when(provider.chat(anyString(), anyString(), any()))
                .thenThrow(new AiProviderException("private-provider-detail"));
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"message\":\"AI assistant is currently unavailable\"}"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCannotInvokeProvider() throws Exception {
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }

    @Test
    void anonymousRequestCannotInvokeProvider() throws Exception {
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(provider);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void missingCsrfCannotInvokeProvider() throws Exception {
        mvc.perform(post("/api/ai/chat").contentType(APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(provider);
    }
}
