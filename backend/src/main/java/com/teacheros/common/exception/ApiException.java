package com.teacheros.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors we expect and want to surface to the client with a
 * specific status/code, as opposed to unexpected exceptions (which the
 * GlobalExceptionHandler maps to a generic 500 and logs internally).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
