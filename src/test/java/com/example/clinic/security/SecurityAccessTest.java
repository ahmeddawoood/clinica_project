package com.example.clinic.security;

import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SecurityAccessTest {

    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;

    @Autowired WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithAnonymousUser
    void unauthenticated_patientDashboard_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/patient/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    void unauthenticated_doctorDashboard_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/doctor/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    void unauthenticated_adminPanel_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    void publicEndpoint_homePage_accessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void publicEndpoint_loginPage_accessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void publicEndpoint_registerPage_accessible() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void patientRole_doctorRoute_forbidden() throws Exception {
        mockMvc.perform(get("/doctor/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void patientRole_adminRoute_forbidden() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorRole_patientRoute_forbidden() throws Exception {
        mockMvc.perform(get("/patient/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorRole_adminRoute_forbidden() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "patient@test.com", roles = "PATIENT")
    void patientRole_patientRoute_authorizationPasses() throws Exception {
        int status = mockMvc.perform(get("/patient/dashboard"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
        assertThat(status).isNotIn(301, 302);
    }

    @Test
    @WithMockUser(username = "doctor@test.com", roles = "DOCTOR")
    void doctorRole_doctorRoute_authorizationPasses() throws Exception {
        int status = mockMvc.perform(get("/doctor/dashboard"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
        assertThat(status).isNotIn(301, 302);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRole_adminRoute_authorizationPasses() throws Exception {
        int status = mockMvc.perform(get("/admin/dashboard"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
        assertThat(status).isNotIn(301, 302);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void csrfProtection_postWithoutCsrfToken_forbidden() throws Exception {
        mockMvc.perform(post("/patient/book"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void csrfProtection_postWithCsrfToken_notForbiddenByCsrf() throws Exception {
        int status = mockMvc.perform(post("/patient/book").with(csrf()))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void csrfProtection_doctorPostWithoutCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/doctor/confirm/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRole_patientRoute_forbidden() throws Exception {
        mockMvc.perform(get("/patient/dashboard"))
                .andExpect(status().isForbidden());
    }
}
