package com.teacheros.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to boot under the "prod" profile with dev/placeholder/blank
 * configuration. Runs as an EnvironmentPostProcessor (registered in
 * META-INF/spring.factories) so it fails BEFORE any bean is created or a
 * DB connection is attempted, and reports every problem in one message.
 * Does nothing under any other profile.
 */
public class ProductionConfigValidator implements EnvironmentPostProcessor, Ordered {

    static final String DEV_JWT_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";
    static final String DEV_DB_URL = "jdbc:postgresql://localhost:5432/teacheros";
    static final String DEV_DB_PASSWORD = "teacheros";
    static final String DEV_STORAGE_ACCESS_KEY = "teacheros";
    static final String DEV_STORAGE_SECRET_KEY = "teacheros123";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problems = findProblems(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with the 'prod' profile - unsafe or missing configuration:\n  - "
                            + String.join("\n  - ", problems)
                            + "\nFix these (usually environment variables) and restart.");
        }
    }

    /** Pure function over an Environment so each rule is unit-testable. */
    public static List<String> findProblems(Environment env) {
        List<String> problems = new ArrayList<>();

        // --- JWT ---
        String jwt = prop(env, "app.jwt.secret");
        if (jwt.isEmpty() || jwt.equals(DEV_JWT_SECRET)) {
            problems.add("JWT_SECRET is missing or still the dev default. Generate one: openssl rand -base64 48");
        } else if (jwt.length() < 32) {
            problems.add("JWT_SECRET must be at least 32 characters.");
        }

        // --- Database ---
        String dbUrl = prop(env, "spring.datasource.url");
        if (dbUrl.isEmpty() || dbUrl.equals(DEV_DB_URL)) {
            problems.add("DB_URL is missing or still the dev default.");
        }
        if (prop(env, "spring.datasource.username").isEmpty()) {
            problems.add("DB_USERNAME is blank.");
        }
        String dbPassword = prop(env, "spring.datasource.password");
        if (dbPassword.isEmpty() || dbPassword.equals(DEV_DB_PASSWORD)) {
            problems.add("DB_PASSWORD is blank or still the dev default.");
        }

        // --- Payments ---
        String payment = prop(env, "app.payment.provider");
        if (payment.isEmpty() || payment.equalsIgnoreCase("mock")) {
            problems.add("PAYMENT_PROVIDER must be 'razorpay' in prod (the mock gateway marks any payment as paid).");
        } else if (payment.equalsIgnoreCase("razorpay")) {
            if (prop(env, "app.payment.razorpay.key-id").isEmpty()) {
                problems.add("RAZORPAY_KEY_ID is blank.");
            }
            if (prop(env, "app.payment.razorpay.key-secret").isEmpty()) {
                problems.add("RAZORPAY_KEY_SECRET is blank.");
            }
        }

        // --- OTP / SMS (Twilio) ---
        String otpMode = prop(env, "app.otp.delivery-mode");
        if (otpMode.isEmpty() || otpMode.equalsIgnoreCase("log")) {
            problems.add("OTP_DELIVERY_MODE must be 'sms' in prod (log mode prints login codes to the console).");
        }
        boolean needsTwilio = otpMode.equalsIgnoreCase("sms")
                || prop(env, "app.sms.provider").equalsIgnoreCase("twilio");
        if (needsTwilio) {
            if (prop(env, "app.sms.twilio.account-sid").isEmpty()) {
                problems.add("TWILIO_ACCOUNT_SID is blank.");
            }
            if (prop(env, "app.sms.twilio.auth-token").isEmpty()) {
                problems.add("TWILIO_AUTH_TOKEN is blank.");
            }
            if (prop(env, "app.sms.twilio.from-number").isEmpty()) {
                problems.add("TWILIO_FROM_NUMBER is blank.");
            }
        }

        // --- CORS ---
        String origins = prop(env, "app.cors.allowed-origins");
        if (origins.isEmpty()) {
            problems.add("CORS_ALLOWED_ORIGINS is blank - set the exact web app origin(s), comma-separated.");
        } else if (List.of(origins.split(",")).stream().map(String::trim).anyMatch("*"::equals)) {
            problems.add("CORS_ALLOWED_ORIGINS must not contain '*' (credentials are allowed).");
        }

        // --- Video (recorded lectures) ---
        String video = prop(env, "app.video.provider");
        if (video.isEmpty() || video.equalsIgnoreCase("log")) {
            problems.add(
                    "VIDEO_PROVIDER must be a real provider ('bunny') in prod (log mode returns fake playback URLs, so no one can watch a lecture).");
        } else if (video.equalsIgnoreCase("bunny")) {
            if (prop(env, "app.video.bunny.library-id").isEmpty()) {
                problems.add("BUNNY_LIBRARY_ID is blank.");
            }
            if (prop(env, "app.video.bunny.api-key").isEmpty()) {
                problems.add("BUNNY_API_KEY is blank.");
            }
            if (prop(env, "app.video.bunny.cdn-host").isEmpty()) {
                problems.add("BUNNY_CDN_HOST is blank.");
            }
            if (prop(env, "app.video.bunny.token-key").isEmpty()) {
                problems.add("BUNNY_TOKEN_KEY is blank (the pull zone security key used to sign playback URLs).");
            }
        } else {
            problems.add("VIDEO_PROVIDER '" + video + "' is not a supported provider (use 'bunny').");
        }

        // --- Object storage ---
        if (prop(env, "app.storage.access-key").equals(DEV_STORAGE_ACCESS_KEY)
                || prop(env, "app.storage.secret-key").equals(DEV_STORAGE_SECRET_KEY)) {
            problems.add("STORAGE_ACCESS_KEY / STORAGE_SECRET_KEY are still the dev MinIO defaults.");
        }

        return problems;
    }

    // An unresolvable ${placeholder} throws; treat that the same as blank.
    private static String prop(Environment env, String key) {
        try {
            String v = env.getProperty(key);
            return v == null ? "" : v.trim();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    @Override
    public int getOrder() {
        // After ConfigDataEnvironmentPostProcessor so application*.yml is loaded.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
