package com.example.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Prenumele este obligatoriu")
    private String firstName;

    @NotBlank(message = "Numele este obligatoriu")
    private String lastName;

    @NotBlank(message = "Email-ul este obligatoriu")
    @Email(message = "Email invalid")
    private String email;

    @NotBlank(message = "Parola este obligatorie")
    @Size(min = 8, message = "Parola trebuie sa aiba minim 8 caractere")
    @Pattern(regexp = ".*\\d.*", message = "Parola trebuie sa contina cel putin o cifra")
    private String password;

    private String phone;

    @NotBlank(message = "Rolul este obligatoriu")
    private String role;

    private String specialty;

    public RegisterRequest() {}

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
}
