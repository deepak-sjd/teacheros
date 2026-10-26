package com.teacheros.modules.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active when app.otp.delivery-mode=log (the dev default). Lets any
 * developer or pilot tester exercise the full login flow without a real
 * SMS/WhatsApp Business API account. Must NOT be active in prod -
 * app.otp.delivery-mode must be set to a real provider before launch,
 * enforced by application-prod.yml.
 */
@Component

@ConditionalOnProperty(name = "app.security.rate-limit.provider", havingValue = "memory", matchIfMissing = true)
public class LoggingOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    @Override
    public void send(String phoneNumber, String code) {
        log.info("[DEV OTP] phone={} code={} (no real SMS sent - dev delivery mode)", phoneNumber, code);
    }
}
