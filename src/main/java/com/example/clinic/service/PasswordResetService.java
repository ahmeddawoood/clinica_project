package com.example.clinic.service;

public interface PasswordResetService {
    void initiateReset(String email);
    boolean isValidToken(String token);
    void resetPassword(String token, String newPassword);
}
