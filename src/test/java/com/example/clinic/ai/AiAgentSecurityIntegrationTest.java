package com.example.clinic.ai;

import com.example.clinic.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AiAgentSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiProvider aiProvider;

    @MockitoBean
    PatientService patientService;

    @BeforeEach
    void setUp() {
        when(aiProvider.chat(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    List<?> tools = invocation.getArgument(2);
                    Object appointmentsTool = tools.stream()
                            .filter(MyAppointmentsTool.class::isInstance)
                            .findFirst()
                            .orElseThrow();
                    ((MyAppointmentsTool) appointmentsTool).getMyAppointments();
                    return "ok";
                });
    }

    @Test
    @WithMockUser(username = "patient-b@test.com", roles = "PATIENT")
    void patientCanOnlyAccessAppointmentsForAuthenticatedIdentity() throws Exception {
        when(patientService.getAppointments("patient-b@test.com")).thenReturn(List.of());

        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Show my appointments\"}"))
                .andExpect(status().isOk());

        verify(patientService).getAppointments("patient-b@test.com");
        verify(patientService, never()).getAppointments("patient-a@test.com");
    }

    @Test
    @WithMockUser(username = "doctor@test.com", roles = "DOCTOR")
    void doctorCannotAccessPatientAiAgent() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Show my appointments\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/403"));

        verify(patientService, never()).getAppointments(anyString());
    }

    @Test
    @WithAnonymousUser
    void anonymousUserCannotAccessPatientAiAgent() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Show my appointments\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(patientService, never()).getAppointments(anyString());
    }
}
