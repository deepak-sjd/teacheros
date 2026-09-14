package com.teacheros.common.exception;

import java.time.Instant;
import java.util.List;

/**
 * Every error the API returns uses this shape, consistently, so the Flutter
 * client can handle errors generically instead of parsing ad-hoc messages.
 * Never put stack traces or internal exception messages in here.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldError> errors,
        String traceId
) {
    public record FieldError(String field, String message) {
    }
}
