package com.example.clinic.service;

import com.example.clinic.domain.User;
import com.example.clinic.dto.RegisterRequest;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends UserDetailsService {
    void register(RegisterRequest request);
    void changePassword(String email, String currentPassword, String newPassword);
    List<User> getAllUsers();
    void toggleUserEnabled(Long userId);
}
