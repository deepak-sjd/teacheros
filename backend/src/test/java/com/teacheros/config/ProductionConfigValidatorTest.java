package com.teacheros.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {

    private MockEnvironment validProd() {
        return new MockEnvironment()
                .withProperty("app.jwt.secret", "a-real-random-secret-that-is-long-enough-1234567890")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/teacheros")
                .withProperty("spring.datasource.username", "teacheros_app")
                .withProperty("spring.datasource.password", "s3cure-db-password")
                .withProperty("app.payment.provider", "razorpay")
                .withProperty("app.payment.razorpay.key-id", "rzp_live_abc")
                .withProperty("app.payment.razorpay.key-secret", "secret")
                .withProperty("app.otp.delivery-mode", "sms")
                .withProperty("app.sms.provider", "twilio")
                .withProperty("app.sms.twilio.account-sid", "AC123")
                .withProperty("app.sms.twilio.auth-token", "tok")
                .withProperty("app.sms.twilio.from-number", "+15550001111")
                .withProperty("app.video.provider", "bunny")
                .withProperty("app.video.bunny.library-id", "12345")
                .withProperty("app.video.bunny.api-key", "bunny-api-key")
                .withProperty("app.video.bunny.cdn-host", "vz-abc.b-cdn.net")
                .withProperty("app.video.bunny.token-key", "bunny-token-key")
                .withProperty("app.cors.allowed-origins", "https://app.teacheros.in")
                .withProperty("app.storage.access-key", "AKIAREAL")
                .withProperty("app.storage.secret-key", "real-storage-secret");
    }

    @Test
    void validProdConfigHasNoProblems() {
        assertThat(ProductionConfigValidator.findProblems(validProd())).isEmpty();
    }

    @Test
    void devJwtSecretIsRejected() {
        MockEnvironment env = validProd().withProperty("app.jwt.secret", ProductionConfigValidator.DEV_JWT_SECRET);
        assertThat(ProductionConfigValidator.findProblems(env)).anyMatch(p -> p.contains("JWT_SECRET"));
    }

    @Test
    void shortJwtSecretIsRejected() {
        MockEnvironment env = validProd().withProperty("app.jwt.secret", "short");
        assertThat(ProductionConfigValidator.findProblems(env)).anyMatch(p -> p.contains("32 characters"));
    }

    @Test
    void mockPaymentProviderIsRejected() {
        MockEnvironment env = validProd().withProperty("app.payment.provider", "mock");
        assertThat(ProductionConfigValidator.findProblems(env)).anyMatch(p -> p.contains("PAYMENT_PROVIDER"));
    }

    @Test
    void blankRazorpayKeysAreRejected() {
        MockEnvironment env = validProd()
                .withProperty("app.payment.razorpay.key-id", "")
                .withProperty("app.payment.razorpay.key-secret", "");
        assertThat(ProductionConfigValidator.findProblems(env))
                .anyMatch(p -> p.contains("RAZORPAY_KEY_ID"))
                .anyMatch(p -> p.contains("RAZORPAY_KEY_SECRET"));
    }

    @Test
    void blankOrDefaultDatabaseValuesAreRejected() {
        MockEnvironment env = validProd()
                .withProperty("spring.datasource.url", ProductionConfigValidator.DEV_DB_URL)
                .withProperty("spring.datasource.username", "")
                .withProperty("spring.datasource.password", ProductionConfigValidator.DEV_DB_PASSWORD);
        assertThat(ProductionConfigValidator.findProblems(env))
                .anyMatch(p -> p.contains("DB_URL"))
                .anyMatch(p -> p.contains("DB_USERNAME"))
                .anyMatch(p -> p.contains("DB_PASSWORD"));
    }

    @Test
    void logOtpModeAndBlankTwilioValuesAreRejected() {
        MockEnvironment env = validProd()
                .withProperty("app.otp.delivery-mode", "log")
                .withProperty("app.sms.provider", "twilio")
                .withProperty("app.sms.twilio.account-sid", "")
                .withProperty("app.sms.twilio.auth-token", "")
                .withProperty("app.sms.twilio.from-number", "");
        assertThat(ProductionConfigValidator.findProblems(env))
                .anyMatch(p -> p.contains("OTP_DELIVERY_MODE"))
                .anyMatch(p -> p.contains("TWILIO_ACCOUNT_SID"))
                .anyMatch(p -> p.contains("TWILIO_AUTH_TOKEN"))
                .anyMatch(p -> p.contains("TWILIO_FROM_NUMBER"));
    }

    @Test
    void wildcardOrBlankCorsIsRejected() {
        assertThat(ProductionConfigValidator.findProblems(
                validProd().withProperty("app.cors.allowed-origins", "https://a.com, *")))
                .anyMatch(p -> p.contains("'*'"));
        assertThat(ProductionConfigValidator.findProblems(
                validProd().withProperty("app.cors.allowed-origins", "")))
                .anyMatch(p -> p.contains("CORS_ALLOWED_ORIGINS"));
    }

    @Test
    void logOrBlankVideoProviderIsRejected() {
        assertThat(ProductionConfigValidator.findProblems(validProd().withProperty("app.video.provider", "log")))
                .anyMatch(p -> p.contains("VIDEO_PROVIDER"));
        assertThat(ProductionConfigValidator.findProblems(validProd().withProperty("app.video.provider", "")))
                .anyMatch(p -> p.contains("VIDEO_PROVIDER"));
    }

    @Test
    void unknownVideoProviderIsRejected() {
        assertThat(ProductionConfigValidator.findProblems(validProd().withProperty("app.video.provider", "vimeo")))
                .anyMatch(p -> p.contains("not a supported provider"));
    }

    @Test
    void blankBunnySettingsAreRejected() {
        MockEnvironment env = validProd()
                .withProperty("app.video.bunny.library-id", "")
                .withProperty("app.video.bunny.api-key", "")
                .withProperty("app.video.bunny.cdn-host", "")
                .withProperty("app.video.bunny.token-key", "");
        assertThat(ProductionConfigValidator.findProblems(env))
                .anyMatch(p -> p.contains("BUNNY_LIBRARY_ID"))
                .anyMatch(p -> p.contains("BUNNY_API_KEY"))
                .anyMatch(p -> p.contains("BUNNY_CDN_HOST"))
                .anyMatch(p -> p.contains("BUNNY_TOKEN_KEY"));
    }

    @Test
    void devStorageCredentialsAreRejected() {
        MockEnvironment env = validProd()
                .withProperty("app.storage.secret-key", ProductionConfigValidator.DEV_STORAGE_SECRET_KEY);
        assertThat(ProductionConfigValidator.findProblems(env)).anyMatch(p -> p.contains("STORAGE_"));
    }

    @Test
    void prodProfileWithBadConfigRefusesToStartAndListsEveryProblem() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.secret", ProductionConfigValidator.DEV_JWT_SECRET)
                .withProperty("app.payment.provider", "mock");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ProductionConfigValidator().postProcessEnvironment(
                new org.springframework.core.env.StandardEnvironment() {
                    {
                        getPropertySources().addFirst(new org.springframework.core.env.PropertiesPropertySource(
                                "test", toProperties(env)));
                        setActiveProfiles("prod");
                    }
                }, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start")
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("PAYMENT_PROVIDER");
    }

    @Test
    void nonProdProfileIsNeverBlocked() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.jwt.secret", ProductionConfigValidator.DEV_JWT_SECRET)
                .withProperty("app.payment.provider", "mock");
        env.setActiveProfiles("dev");

        assertThatCode(() -> {
            org.springframework.core.env.StandardEnvironment std = new org.springframework.core.env.StandardEnvironment();
            std.getPropertySources().addFirst(
                    new org.springframework.core.env.PropertiesPropertySource("test", toProperties(env)));
            std.setActiveProfiles("dev");
            new ProductionConfigValidator().postProcessEnvironment(std, null);
        }).doesNotThrowAnyException();
    }

    private static java.util.Properties toProperties(MockEnvironment env) {
        java.util.Properties p = new java.util.Properties();
        for (String key : new String[] { "app.jwt.secret", "app.payment.provider" }) {
            String v = env.getProperty(key);
            if (v != null) {
                p.setProperty(key, v);
            }
        }
        return p;
    }
}
