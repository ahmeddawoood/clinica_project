-- Initial schema matching the JPA mappings. Do not edit after deployment.

CREATE TABLE `activity_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `appointment_id` BIGINT NOT NULL,
    `action` VARCHAR(50) NOT NULL,
    `timestamp` DATETIME(6) NOT NULL,
    `description` VARCHAR(500),
    INDEX `idx_activity_log_appointment` (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `app_notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `type` VARCHAR(50) NOT NULL,
    `message` VARCHAR(500) NOT NULL,
    `appointment_id` BIGINT,
    `actor_name` VARCHAR(150),
    `created_at` DATETIME(6) NOT NULL,
    `is_read` BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `appointment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `patient_id` BIGINT,
    `doctor_id` BIGINT,
    `appointment_date` DATETIME(6),
    `status` VARCHAR(255),
    `notes` VARCHAR(255),
    `cancel_reason` VARCHAR(255),
    `priority` VARCHAR(255),
    `created_at` DATETIME(6),
    `booking_fee_paid` BIT(1) NOT NULL,
    `booking_fee_refunded` BIT(1) NOT NULL,
    `stripe_payment_intent_id` VARCHAR(500),
    `stripe_session_id` VARCHAR(500),
    `stripe_payment_status` VARCHAR(255),
    `stripe_amount_cents` BIGINT,
    `stripe_currency` VARCHAR(255),
    `stripe_paid_at` DATETIME(6),
    `stripe_refund_id` VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `timestamp` DATETIME(6) NOT NULL,
    `actor_email` VARCHAR(255),
    `action` VARCHAR(100) NOT NULL,
    `details` VARCHAR(1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `doctor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `first_name` VARCHAR(255),
    `last_name` VARCHAR(255),
    `specialty` VARCHAR(255),
    `phone` VARCHAR(255),
    `user_id` BIGINT UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `doctor_schedule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `doctor_id` BIGINT NOT NULL,
    `day_of_week` INT NOT NULL,
    `start_hour` INT NOT NULL,
    `end_hour` INT NOT NULL,
    `available` BIT(1) NOT NULL,
    CONSTRAINT `uk_doctor_schedule_day` UNIQUE (`doctor_id`, `day_of_week`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoice` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `patient_id` BIGINT NOT NULL,
    `appointment_id` BIGINT,
    `amount` DECIMAL(10,2) NOT NULL,
    `description` VARCHAR(255),
    `status` VARCHAR(255) NOT NULL,
    `issued_at` DATETIME(6) NOT NULL,
    `paid_at` DATETIME(6),
    `payment_method` VARCHAR(255),
    `stripe_payment_intent_id` VARCHAR(500),
    `stripe_session_id` VARCHAR(500),
    `stripe_refund_id` VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medical_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `patient_id` BIGINT NOT NULL,
    `doctor_id` BIGINT NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `symptoms` VARCHAR(255),
    `diagnosis` VARCHAR(255),
    `treatment` TEXT,
    `notes` TEXT,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medical_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `appointment_id` BIGINT NOT NULL,
    `doctor_id` BIGINT NOT NULL,
    `patient_id` BIGINT NOT NULL,
    `diagnosis` VARCHAR(1000) NOT NULL,
    `treatment` VARCHAR(2000),
    `notes` VARCHAR(2000),
    `generated_at` DATETIME(6) NOT NULL,
    `pdf_data` LONGBLOB
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `sender_id` BIGINT NOT NULL,
    `receiver_id` BIGINT NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `body` TEXT NOT NULL,
    `sent_at` DATETIME(6) NOT NULL,
    `is_read` BIT(1) NOT NULL,
    `parent_id` BIGINT,
    `edited_at` DATETIME(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `password_reset_token` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `token` VARCHAR(64) NOT NULL UNIQUE,
    `user_id` BIGINT NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `used` BIT(1) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `patient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `first_name` VARCHAR(255),
    `last_name` VARCHAR(255),
    `phone` VARCHAR(255),
    `gender` VARCHAR(255),
    `date_of_birth` DATE,
    `blood_type` VARCHAR(255),
    `allergies` TEXT,
    `address` TEXT,
    `user_id` BIGINT UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `prescription` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `patient_id` BIGINT NOT NULL,
    `doctor_id` BIGINT NOT NULL,
    `appointment_id` BIGINT,
    `medications` TEXT NOT NULL,
    `instructions` TEXT,
    `diagnosis` VARCHAR(255),
    `issued_at` DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `rating` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `appointment_id` BIGINT NOT NULL UNIQUE,
    `doctor_id` BIGINT NOT NULL,
    `patient_id` BIGINT NOT NULL,
    `stars` INT NOT NULL,
    `comment` TEXT,
    `created_at` DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `app_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `role` VARCHAR(255) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `app_notification` ADD CONSTRAINT `fk_app_notification_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);
ALTER TABLE `appointment` ADD CONSTRAINT `fk_appointment_patient_id` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`);
ALTER TABLE `appointment` ADD CONSTRAINT `fk_appointment_doctor_id` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`);
ALTER TABLE `doctor` ADD CONSTRAINT `fk_doctor_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);
ALTER TABLE `doctor_schedule` ADD CONSTRAINT `fk_doctor_schedule_doctor_id` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`);
ALTER TABLE `invoice` ADD CONSTRAINT `fk_invoice_patient_id` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`);
ALTER TABLE `invoice` ADD CONSTRAINT `fk_invoice_appointment_id` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`);
ALTER TABLE `medical_record` ADD CONSTRAINT `fk_medical_record_patient_id` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`);
ALTER TABLE `medical_record` ADD CONSTRAINT `fk_medical_record_doctor_id` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`);
ALTER TABLE `medical_report` ADD CONSTRAINT `fk_medical_report_appointment_id` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`);
ALTER TABLE `medical_report` ADD CONSTRAINT `fk_medical_report_doctor_id` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`);
ALTER TABLE `medical_report` ADD CONSTRAINT `fk_medical_report_patient_id` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`);
ALTER TABLE `message` ADD CONSTRAINT `fk_message_sender_id` FOREIGN KEY (`sender_id`) REFERENCES `app_user` (`id`);
ALTER TABLE `message` ADD CONSTRAINT `fk_message_receiver_id` FOREIGN KEY (`receiver_id`) REFERENCES `app_user` (`id`);
ALTER TABLE `message` ADD CONSTRAINT `fk_message_parent_id` FOREIGN KEY (`parent_id`) REFERENCES `message` (`id`);
ALTER TABLE `password_reset_token` ADD CONSTRAINT `fk_password_reset_token_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);
ALTER TABLE `patient` ADD CONSTRAINT `fk_patient_user_id` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);
ALTER TABLE `prescription` ADD CONSTRAINT `fk_prescription_patient_id` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`);
ALTER TABLE `prescription` ADD CONSTRAINT `fk_prescription_doctor_id` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`);
ALTER TABLE `prescription` ADD CONSTRAINT `fk_prescription_appointment_id` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`);
ALTER TABLE `rating` ADD CONSTRAINT `fk_rating_appointment_id` FOREIGN KEY (`appointment_id`) REFERENCES `appointment` (`id`);
ALTER TABLE `rating` ADD CONSTRAINT `fk_rating_doctor_id` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`);
ALTER TABLE `rating` ADD CONSTRAINT `fk_rating_patient_id` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`);
