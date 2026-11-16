package com.example.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CustomLoginSuccessHandlerTest {
    private void verify(String role, String path, String method, String id, String expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServerName("untrusted.example");
        if (id != null) request.addParameter("doctorId", id);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.saveRequest(request, response);
        new CustomLoginSuccessHandler().onAuthenticationSuccess(request, response,
                new UsernamePasswordAuthenticationToken("fixture", null,
                        List.of(new SimpleGrantedAuthority(role))));
        assertThat(response.getRedirectedUrl()).isEqualTo(expected);
        assertThat(cache.getRequest(request, response)).isNull();
    }

    @Test void patientResumesBookingUsingOnlyLocalPath() throws Exception {
        verify("ROLE_PATIENT", "/patient/book", "GET", "12", "/patient/book?doctorId=12");
    }
    @Test void malformedDoctorFallsBackToDashboard() throws Exception {
        for (String id : new String[]{"0", "-1", "abc", "1&next=https://example.com", "999999999999999999999"}) {
            verify("ROLE_PATIENT", "/patient/book", "GET", id, "/patient/dashboard");
        }
    }
    @Test void otherRequestsDoNotBecomeLoginDestinations() throws Exception {
        verify("ROLE_PATIENT", "/admin/dashboard", "GET", "12", "/patient/dashboard");
        verify("ROLE_PATIENT", "/patient/book", "POST", "12", "/patient/dashboard");
        verify("ROLE_PATIENT", "/patient/book", "GET", null, "/patient/dashboard");
    }
    @Test void staffStillReachTheirOwnDashboards() throws Exception {
        verify("ROLE_DOCTOR", "/patient/book", "GET", "12", "/doctor/dashboard");
        verify("ROLE_ADMIN", "/patient/book", "GET", "12", "/admin/dashboard");
    }
    @Test void ordinaryLoginKeepsPatientDashboard() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CustomLoginSuccessHandler().onAuthenticationSuccess(request, response,
                new UsernamePasswordAuthenticationToken("fixture", null,
                        List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        assertThat(response.getRedirectedUrl()).isEqualTo("/patient/dashboard");
    }
}
