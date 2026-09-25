package com.teacheros.modules.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev default - prints the email instead of sending a real one. */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String toAddress, String subject, String body) {
        log.info("[DEV EMAIL] to={} subject={} body={} (no real email sent - dev provider)",
                toAddress, subject, body);
    }
}
