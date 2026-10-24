package com.teacheros.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal data in log text: phone numbers (kept: last 4 digits), emails,
 * personal-name fields written as key=value / JSON, bearer tokens, and OTP
 * codes -
 * except on the "[DEV OTP]" line printed by the dev-only log OTP provider.
 *
 * Limits: this is pattern based. A bare name in free text can't be detected, so
 * never log a person's name outside the fields above. A 10-15 digit number that
 * isn't a phone (e.g. an epoch-millis value) will also be masked.
 */
public final class LogMasker {

    private LogMasker() {
    }

    private static final Pattern PHONE_PLAIN = Pattern.compile("(?<![\\w.-])\\+?\\d{10,15}(?![\\w-])");
    private static final Pattern PHONE_SPACED = Pattern
            .compile("(?<![\\w.-])(?:\\+\\d{1,3}[ -]\\d{5}[ -]\\d{5}|\\d{5}[ -]\\d{5})(?![\\w-])");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@([\\w-]+\\.[\\w.-]+)");
    private static final String NAME_KEYS = "fullName|displayName|studentName|guardianName|parentName|senderName";
    private static final Pattern NAME_KV = Pattern.compile("(?i)\\b(" + NAME_KEYS + ")=([^,;)\\]}\\r\\n]+)");
    private static final Pattern NAME_JSON = Pattern.compile("(?i)(\"(?:" + NAME_KEYS + ")\"\\s*:\\s*\")([^\"]*)(\")");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern OTP_KV = Pattern.compile("(?i)\\b(otp|code)=\\d{4,8}\\b");
    private static final Pattern OTP_JSON = Pattern.compile("(?i)(\"(?:otp|code)\"\\s*:\\s*\")\\d{4,8}(\")");
    private static final Pattern OTP_SENTENCE = Pattern.compile("(?i)(your (?:\\w+ )?(?:code|otp) is )\\d{4,8}");

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        if (!out.contains("[DEV OTP]")) {
            out = OTP_KV.matcher(out).replaceAll("$1=******");
            out = OTP_JSON.matcher(out).replaceAll("$1******$2");
            out = OTP_SENTENCE.matcher(out).replaceAll("$1******");
        }
        out = BEARER.matcher(out).replaceAll("Bearer ***");
        out = NAME_JSON.matcher(out).replaceAll("$1***$3");
        out = NAME_KV.matcher(out).replaceAll("$1=***");
        out = EMAIL.matcher(out).replaceAll("***@$1");
        out = maskPhones(PHONE_SPACED, out);
        out = maskPhones(PHONE_PLAIN, out);
        return out;
    }

    private static String maskPhones(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String digits = m.group().replaceAll("\\D", "");
            m.appendReplacement(sb, Matcher.quoteReplacement("******" + digits.substring(digits.length() - 4)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
