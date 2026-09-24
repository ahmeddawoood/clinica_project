package com.example.clinic.service;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.RegisterRequest;
import com.example.clinic.exception.ClinicException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           PatientRepository patientRepository,
                           DoctorRepository doctorRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (!"PATIENT".equals(request.getRole()) && !"DOCTOR".equals(request.getRole())) {
            throw new IllegalArgumentException("Rolul nu poate fi creat prin inregistrarea publica.");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed: email already in use - {}", request.getEmail());
            throw new IllegalArgumentException("Email-ul este deja folosit");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        userRepository.save(user);
        log.info("New user registered: email={} role={}", request.getEmail(), request.getRole());

        if ("PATIENT".equals(request.getRole())) {
            Patient patient = new Patient();
            patient.setFirstName(request.getFirstName());
            patient.setLastName(request.getLastName());
            patient.setPhone(request.getPhone());
            patient.setUser(user);
            patientRepository.save(patient);
        } else if ("DOCTOR".equals(request.getRole())) {
            Doctor doctor = new Doctor();
            doctor.setFirstName(request.getFirstName());
            doctor.setLastName(request.getLastName());
            doctor.setPhone(request.getPhone());
            doctor.setSpecialty(request.getSpecialty());
            doctor.setUser(user);
            doctorRepository.save(doctor);
        }
    }

    @Override
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilizatorul nu a fost găsit."));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Parola curentă este incorectă.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for: {}", email);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByRoleAscEmailAsc();
    }

    @Override
    @Transactional
    public void toggleUserEnabled(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        if ("ADMIN".equals(user.getRole())) {
            throw new ClinicException("Contul de administrator nu poate fi dezactivat.");
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("User {} toggled to enabled={}", user.getEmail(), user.isEnabled());
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}
