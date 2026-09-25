package com.teacheros.modules.notification;

/**
 * Free-text SMS for alerts (low attendance, fee due/overdue) - separate
 * from auth.OtpSender, which is OTP-specific (fixed "your code is X"
 * format, different delivery-mode config key). Same provider under the
 * hood in practice, but kept as its own abstraction so alert delivery can
 * be toggled/swapped independently of login OTP delivery.
 */
public interface SmsSender {
    void send(String phoneNumber, String message);
}
