package com.example.clinic.controller;

import com.example.clinic.service.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam String email) {
        passwordResetService.initiateReset(email);
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank() || !passwordResetService.isValidToken(token)) {
            model.addAttribute("tokenError", true);
        }
        model.addAttribute("token", token != null ? token : "");
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(@RequestParam String token,
                                      @RequestParam String password,
                                      @RequestParam String confirmPassword,
                                      Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Parolele nu coincid.");
            return "auth/reset-password";
        }
        if (password.length() < 6) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Parola trebuie să aibă cel puțin 6 caractere.");
            return "auth/reset-password";
        }
        try {
            passwordResetService.resetPassword(token, password);
            return "redirect:/login?passwordReset";
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("error", e.getMessage());
            return "auth/reset-password";
        }
    }
}
