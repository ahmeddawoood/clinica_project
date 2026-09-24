package com.example.clinic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ResourceAccessDeniedException extends RuntimeException {

    public ResourceAccessDeniedException(String message) {
        super(message);
    }
}
