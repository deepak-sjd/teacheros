package com.teacheros.modules.auth;

import com.teacheros.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A wrong OTP code. It is its own type so AuthService.verifyOtp can tell Spring
 * to COMMIT the failed-attempt count instead of rolling it back (a plain
 * ApiException is a RuntimeException, which rolls the transaction back).
 */
public class OtpIncorrectException extends ApiException {

    public OtpIncorrectException() {
        super(HttpStatus.BAD_REQUEST, "OTP_INCORRECT", "That code is incorrect.");
    }
}
