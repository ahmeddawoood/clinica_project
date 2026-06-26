package com.example.clinic.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.clinic.dto.RegisterRequest;
import com.example.clinic.service.UserService;

import jakarta.validation.Valid;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterRequest());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterRequest form,
                           BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        if ("DOCTOR".equals(form.getRole())
                && (form.getSpecialty() == null || form.getSpecialty().isBlank())) {
            model.addAttribute("specialtyError",
                    "Pentru un cont de doctor trebuie sa selectezi o specializare.");
            return "auth/register";
        }

        try {
            userService.register(form);
            log.info("Cont nou inregistrat: {} ({})", form.getEmail(), form.getRole());
        } catch (IllegalArgumentException e) {
            model.addAttribute("emailError", e.getMessage());
            return "auth/register";
        }
        String successType = "DOCTOR".equals(form.getRole()) ? "doctor" : "patient";
        return "redirect:/register?success&type=" + successType;
    }
}
