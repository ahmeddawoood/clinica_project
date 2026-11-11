package com.example.clinic.integration;

import com.example.clinic.ai.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class AiAgentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @MockitoBean
    private AiProvider aiProvider;

    @Test
    @WithMockUser(username = "patient@example.com", roles = "PATIENT")
    void patientCanReachAiAgentAndProviderReceivesAuthorizedTools() throws Exception {
        when(aiProvider.chat(anyString(), anyString(), any())).thenReturn("integration-ok");

        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Show me my appointments\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"message\":\"integration-ok\"}"));

        ArgumentCaptor<List> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiProvider).chat(anyString(), anyString(), toolsCaptor.capture());

        List<?> tools = toolsCaptor.getValue();
        assertThat(tools).hasSize(5);
        assertThat(tools)
                .extracting(tool -> tool.getClass().getSimpleName())
                .containsExactlyInAnyOrder(
                        "MyAppointmentsTool",
                        "MyPrescriptionsTool",
                        "MyMedicalHistoryTool",
                        "DoctorInformationTool",
                        "AvailableDoctorsTool");
    }

    @Test
    @WithMockUser(username = "doctor@example.com", roles = "DOCTOR")
    void nonPatientCannotUseAiAgent() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotUseAiAgent() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
