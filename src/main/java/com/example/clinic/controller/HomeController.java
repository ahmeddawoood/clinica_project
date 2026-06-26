package com.example.clinic.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/403")
    public String forbidden(Model model) {
        model.addAttribute("message", "Nu aveți permisiunea de a accesa această resursă.");
        return "error/403";
    }

    @GetMapping("/")
    public String home(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            for (GrantedAuthority role : auth.getAuthorities()) {
                if (role.getAuthority().equals("ROLE_ADMIN")) return "redirect:/admin/dashboard";
                if (role.getAuthority().equals("ROLE_DOCTOR")) return "redirect:/doctor/dashboard";
                if (role.getAuthority().equals("ROLE_PATIENT")) return "redirect:/patient/dashboard";
            }
        }
        return "home";
    }
}
