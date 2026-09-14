package com.teacheros.modules.auth;

/**
 * Abstraction over "how does the OTP actually reach the teacher's phone."
 * Swap the implementation per environment/config instead of hardcoding a
 * provider into AuthService - this is the same pattern the notification
 * subsystem will use for parent messages (SMS vs WhatsApp vs push), decided
 * once, applied consistently.
 */
public interface OtpSender {
    void send(String phoneNumber, String code);
}
