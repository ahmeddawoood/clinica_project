package com.example.clinic.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;

public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        SavedRequest saved = cache.getRequest(request, response);
        cache.removeRequest(request, response);
        String bookingTarget = null;
        if (saved instanceof DefaultSavedRequest bookingRequest && "GET".equals(saved.getMethod())
                && (request.getContextPath() + "/patient/book").equals(bookingRequest.getRequestURI())) {
            String[] ids = saved.getParameterValues("doctorId");
            if (ids != null && ids.length == 1 && ids[0].matches("[1-9][0-9]{0,18}")) {
                bookingTarget = request.getContextPath() + "/patient/book?doctorId=" + ids[0];
            }
        }

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
                response.sendRedirect(bookingTarget != null ? bookingTarget : "/patient/dashboard");
                return;
            }
        }

        response.sendRedirect("/");
    }
}
