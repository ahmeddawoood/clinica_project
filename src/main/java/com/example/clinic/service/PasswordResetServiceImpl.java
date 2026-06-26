package com.example.clinic.service;

import com.example.clinic.domain.PasswordResetToken;
import com.example.clinic.domain.User;
import com.example.clinic.repository.PasswordResetTokenRepository;
import com.example.clinic.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final int TOKEN_EXPIRY_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8082}")
    private String baseUrl;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetTokenRepository tokenRepository,
                                    NotificationService notificationService,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void initiateReset(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for unknown email: {}", email);
            return;
        }

        User user = userOpt.get();
        tokenRepository.deleteByUser(user);
        tokenRepository.deleteAllExpiredBefore(LocalDateTime.now());

        String token = generateToken();
        LocalDateTime expiry = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);
        tokenRepository.save(new PasswordResetToken(token, user, expiry));

        String resetLink = baseUrl + "/reset-password?token=" + token;
        notificationService.sendPasswordResetEmail(email, resetLink);
        log.info("Password reset initiated for: {}", email);
    }

    @Override
    public boolean isValidToken(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> !t.isUsed() && !t.isExpired())
                .orElse(false);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Link-ul de resetare este invalid sau a expirat."));

        if (resetToken.isUsed()) throw new IllegalArgumentException("Token-ul a fost deja folosit.");
        if (resetToken.isExpired()) throw new IllegalArgumentException("Token-ul a expirat. Solicitați un nou link.");

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Password reset successfully for: {}", user.getEmail());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
