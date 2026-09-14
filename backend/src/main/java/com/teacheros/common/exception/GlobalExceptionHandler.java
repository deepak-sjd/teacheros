package com.teacheros.common.exception;

import com.teacheros.common.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Every exception in the application funnels through here and comes out as
 * a consistent ErrorResponse. Clients never see a raw stack trace or an
 * internal exception message - those go to the log, tagged with the same
 * traceId that's returned to the client, so support can find the exact
 * request from a user's bug report.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("API exception: code={} message={}", ex.getCode(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", fieldErrors);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to do that", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        // This is the only place we log a full stack trace - everything else
        // above is an expected condition and doesn't need one.
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our end. Please try again.", null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message,
                                                          List<ErrorResponse.FieldError> errors) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), code, message, errors, traceId);
        return ResponseEntity.status(status).body(body);
    }
}
