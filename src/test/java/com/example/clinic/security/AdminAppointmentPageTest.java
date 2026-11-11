package com.example.clinic.security;

import com.example.clinic.service.NotificationService;
import com.example.clinic.service.StripeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@ActiveProfiles("test")
class AdminAppointmentPageTest {
    @MockitoBean NotificationService notificationService;
    @MockitoBean StripeService stripeService;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rendersSearchAndEmptyPageWithOutOfRangeInput() throws Exception {
        mvc.perform(get("/admin/appointments").param("q", " nobody ").param("page", "99999999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("q", "nobody"))
                .andExpect(content().string(containsString("Nicio programare")))
                .andExpect(content().string(containsString("Pagina 1 / 1")));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rejectsInvalidFilters() throws Exception {
        mvc.perform(get("/admin/appointments").param("page", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/appointments").param("status", "UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/appointments").param("q", "a".repeat(101))).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(roles = "PATIENT")
    void patientCannotSearchOtherPatients() throws Exception {
        mvc.perform(get("/admin/appointments").param("q", "Ion")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "DOCTOR")
    void doctorCannotSearchAllAppointments() throws Exception {
        mvc.perform(get("/admin/appointments")).andExpect(status().isForbidden());
    }
}
