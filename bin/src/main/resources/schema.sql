
-- MediClinic - Database Schema (documentation)
-- Tables are auto-created by Hibernate (ddl-auto=update).
-- This file is provided for reference and manual setup.

CREATE DATABASE IF NOT EXISTS clinic
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE clinic;

-- Users (authentication)
CREATE TABLE IF NOT EXISTS app_user (
    id       BIGINT       AUTO_INCREMENT PRIMARY KEY,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(50)  NOT NULL          -- ADMIN | DOCTOR | PATIENT
);

-- Patient profiles
CREATE TABLE IF NOT EXISTS patient (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    phone         VARCHAR(20),
    gender        VARCHAR(20),
    date_of_birth DATE,
    blood_type    VARCHAR(5),
    allergies     TEXT,
    address       TEXT,
    user_id       BIGINT UNIQUE,
    CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Doctor profiles
CREATE TABLE IF NOT EXISTS doctor (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    specialty  VARCHAR(100),
    phone      VARCHAR(20),
    user_id    BIGINT UNIQUE,
    CONSTRAINT fk_doctor_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

-- Appointments
CREATE TABLE IF NOT EXISTS appointment (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    patient_id       BIGINT       NOT NULL,
    doctor_id        BIGINT       NOT NULL,
    appointment_date DATETIME     NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                           -- PENDING | CONFIRMED | CANCELLED | COMPLETED
    notes            LONGTEXT,
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_appt_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctor(id)
);

-- Medical records
CREATE TABLE IF NOT EXISTS medical_record (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT      NOT NULL,
    doctor_id  BIGINT      NOT NULL,
    title      VARCHAR(255) NOT NULL,
    symptoms   TEXT,
    diagnosis  VARCHAR(255),
    treatment  TEXT,
    notes      TEXT,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_record_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_record_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctor(id)
);

-- Prescriptions
CREATE TABLE IF NOT EXISTS prescription (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id     BIGINT NOT NULL,
    doctor_id      BIGINT NOT NULL,
    appointment_id BIGINT,
    diagnosis      VARCHAR(255),
    medications    TEXT   NOT NULL,
    instructions   TEXT,
    issued_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rx_patient     FOREIGN KEY (patient_id)     REFERENCES patient(id),
    CONSTRAINT fk_rx_doctor      FOREIGN KEY (doctor_id)      REFERENCES doctor(id),
    CONSTRAINT fk_rx_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id)
);

-- Invoices (billing)
CREATE TABLE IF NOT EXISTS invoice (
    id             BIGINT         AUTO_INCREMENT PRIMARY KEY,
    patient_id     BIGINT         NOT NULL,
    appointment_id BIGINT,
    amount         DECIMAL(10, 2) NOT NULL,
    description    VARCHAR(255),
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
                                           -- PENDING | PAID | CANCELLED
    issued_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at        DATETIME,
    CONSTRAINT fk_inv_patient     FOREIGN KEY (patient_id)     REFERENCES patient(id),
    CONSTRAINT fk_inv_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id)
);
