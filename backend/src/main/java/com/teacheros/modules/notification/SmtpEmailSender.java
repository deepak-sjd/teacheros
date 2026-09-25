package com.teacheros.modules.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real email delivery via SMTP (works with SES, SendGrid, Gmail relay, etc).
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.email.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String toAddress, String subject, String body) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromAddress);
            mail.setTo(toAddress);
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
        } catch (Exception e) {
            // Same reasoning as TwilioSmsAlertSender - never break the
            // triggering staff action over a delivery failure.
            log.error("Failed to send email to {}: {}", toAddress, e.getMessage());
        }
    }
}
