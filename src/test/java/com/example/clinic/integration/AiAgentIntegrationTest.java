package com.example.clinic.integration;

import com.example.clinic.ai.AiProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAgentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

        ArgumentCaptor<List<Object>> toolsCaptor = forClass(List.class);
        verify(aiProvider).chat(anyString(), anyString(), toolsCaptor.capture());

        List<Object> tools = toolsCaptor.getValue();
        assertThat(tools).hasSize(4);
        assertThat(tools)
                .extracting(tool -> tool.getClass().getSimpleName())
                .containsExactlyInAnyOrder(
                        "MyAppointmentsTool",
                        "MyPrescriptionsTool",
                        "MyMedicalHistoryTool",
                        "DoctorInformationTool");
    }

    @Test
    @WithMockUser(username = "doctor@example.com", roles = "DOCTOR")
    void nonPatientCannotUseAiAgent() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/403"));
    }

    @Test
    void unauthenticatedUserCannotUseAiAgent() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
