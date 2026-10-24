package com.teacheros.common.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Test
    void masksPhonesKeepingLastFour() {
        assertThat(LogMasker.mask("to=+919876543210 sent")).isEqualTo("to=******3210 sent");
        assertThat(LogMasker.mask("call 98765 43210 now")).isEqualTo("call ******3210 now");
        assertThat(LogMasker.mask("p=+91 98765 43210.")).contains("******3210").doesNotContain("98765");
    }

    @Test
    void leavesUuidsAndShortNumbersAlone() {
        String s = "id=3f2b8c1e-4a5d-4e6f-8a9b-123456789012 count=42 port=8080";
        assertThat(LogMasker.mask(s)).isEqualTo(s);
    }

    @Test
    void masksNamesEmailsTokensAndOtpCodes() {
        String out = LogMasker.mask("student fullName=Asha Verma, email asha@school.in, "
                + "Authorization: Bearer abc.def.ghi, otp=123456, {\"displayName\":\"Raj\"}");
        assertThat(out).doesNotContain("Asha Verma").doesNotContain("asha@")
                .doesNotContain("abc.def.ghi").doesNotContain("123456").doesNotContain("Raj");
        assertThat(out).contains("fullName=***").contains("***@school.in").contains("otp=******");
    }

    @Test
    void devOtpLineKeepsTheCodeButStillMasksThePhone() {
        String out = LogMasker.mask("[DEV OTP] phone=+919876543210 code=123456 (no real SMS sent)");
        assertThat(out).contains("code=123456").contains("******3210").doesNotContain("919876543210");
    }

    @Test
    void otpInASentenceIsMasked() {
        assertThat(LogMasker.mask("Your login code is 654321")).doesNotContain("654321");
    }
}
