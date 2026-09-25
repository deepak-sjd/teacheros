package com.teacheros.modules.notification;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real SMS delivery for attendance/fee alerts via Twilio. Twilio.init(...)
 * is called once at startup by TwilioConfig - this class just sends.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
public class TwilioSmsAlertSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsAlertSender.class);

    private final String fromNumber;

    public TwilioSmsAlertSender(@Value("${app.sms.twilio.from-number}") String fromNumber) {
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(String phoneNumber, String message) {
        try {
            Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(fromNumber), message).create();
        } catch (Exception e) {
            // Alert delivery failing must never break the staff action that
            // triggered it (e.g. "generate fee reminders") - the in-app
            // Notification row is already saved by this point regardless.
            log.error("Failed to send SMS alert to {}: {}", phoneNumber, e.getMessage());
        }
    }
}
