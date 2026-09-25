package com.teacheros.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Initializes the Twilio SDK once at startup. Active if either OTP or alert
 * SMS is configured to use Twilio - both TwilioOtpSender and
 * TwilioSmsAlertSender rely on Twilio.init(...) having already run.
 */
@Component
@ConditionalOnExpression("'${app.otp.delivery-mode}' == 'sms' or '${app.sms.provider:log}' == 'twilio'")
public class TwilioConfig {

    private final String accountSid;
    private final String authToken;

    public TwilioConfig(
            @Value("${app.sms.twilio.account-sid}") String accountSid,
            @Value("${app.sms.twilio.auth-token}") String authToken) {
        this.accountSid = accountSid;
        this.authToken = authToken;
    }

    @PostConstruct
    void init() {
        Twilio.init(accountSid, authToken);
    }
}
