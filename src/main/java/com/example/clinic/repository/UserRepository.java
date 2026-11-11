package com.example.clinic.repository;

import com.example.clinic.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByRole(String role);
    Optional<User> findByEmail(String email);
    java.util.List<User> findAllByOrderByRoleAscEmailAsc();
}
