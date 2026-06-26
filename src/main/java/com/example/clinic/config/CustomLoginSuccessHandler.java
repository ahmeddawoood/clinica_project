package com.example.clinic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        for (GrantedAuthority role : authentication.getAuthorities()) {
            if (role.getAuthority().equals("ROLE_ADMIN")) {
                response.sendRedirect("/admin/dashboard");
                return;
            }
            if (role.getAuthority().equals("ROLE_DOCTOR")) {
                response.sendRedirect("/doctor/dashboard");
                return;
            }
            if (role.getAuthority().equals("ROLE_PATIENT")) {
                response.sendRedirect("/patient/dashboard");
                return;
            }
        }

        response.sendRedirect("/");
    }
}
