package com.example.clinic.exception;

import org.springframework.security.access.AccessDeniedException;

public class ResourceAccessDeniedException extends AccessDeniedException {

    public ResourceAccessDeniedException(String message) {
        super(message);
    }
}
