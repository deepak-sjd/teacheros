package com.teacheros.modules.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private String provider = "memory";
    private boolean failOpen = true;
    private int windowSeconds = 60;
    private int ipPerWindow = 300;
    private int userPerWindow = 600;
    private int authPerWindow = 60;
    private int enquiryPerWindow = 10;
    private int webhookPerWindow = 120;
    private String enquiryPath = "/api/v1/public/enquiries";
    private String webhookPathPrefix = "/api/v1/payments/webhook";
}
