package com.teacheros.modules.audit;

import com.teacheros.common.tenant.CurrentTenantProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes who-did-what to audit_log. Tenant and actor always come from the
 * JWT via CurrentTenantProvider. The row is written in the CALLER'S
 * transaction, so an audit row exists exactly when the action committed.
 *
 * Metadata is sanitised before storage: keys that look like secrets (otp,
 * code, token, password, secret, signature) are dropped, and any phone
 * number - by key name or by value shape - is masked to its last 4 digits.
 */
@Service
public class AuditService {

    private static final Pattern PHONE_VALUE = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Set<String> BLOCKED_KEY_FRAGMENTS = Set.of("otp", "code", "token", "password", "secret",
            "signature");

    private final AuditLogRepository auditLogRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public AuditService(AuditLogRepository auditLogRepository, CurrentTenantProvider currentTenantProvider) {
        this.auditLogRepository = auditLogRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public void record(String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        auditLogRepository.save(new AuditLog(
                currentTenantProvider.getCurrentTenantId(),
                currentTenantProvider.getCurrentUserId(),
                action, resourceType, resourceId, sanitize(metadata)));
    }

    /**
     * Builds a metadata map from key/value pairs; unlike Map.of, null values are
     * allowed.
     */
    public static Map<String, Object> meta(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    /** Keeps only the last 4 characters, e.g. +919876543210 -> *********3210. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    static Map<String, Object> sanitize(Map<String, Object> metadata) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (metadata == null) {
            return clean;
        }
        for (Map.Entry<String, Object> e : metadata.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase();
            if (BLOCKED_KEY_FRAGMENTS.stream().anyMatch(lower::contains)) {
                continue;
            }
            Object value = e.getValue();
            if (value instanceof String s) {
                clean.put(key, (lower.contains("phone") || PHONE_VALUE.matcher(s).matches()) ? maskPhone(s) : s);
            } else if (value == null || value instanceof Number || value instanceof Boolean) {
                clean.put(key, value);
            } else {
                clean.put(key, String.valueOf(value)); // UUIDs, dates, enums -> plain strings
            }
        }
        return clean;
    }
}
