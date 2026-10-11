package com.teacheros.modules.feestructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacheros.common.tenant.CurrentTenantProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Writes to the existing audit_log table (V1).
 *
 * TODO(Dev 1): replace the body with Dev 1's audit service once merged; the
 * call sites here do not need to change. Runs in the caller's transaction, so
 * an audit row is never written for a change that rolled back.
 */
@Component
public class FeeAuditWriter {

    private static final String SQL = """
            INSERT INTO audit_log (tenant_id, actor_user_id, action, resource_type, resource_id, metadata)
            VALUES (?, ?::uuid, ?, ?, ?::uuid, ?::jsonb)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeAuditWriter(JdbcTemplate jdbc, ObjectMapper objectMapper, CurrentTenantProvider currentTenantProvider) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.currentTenantProvider = currentTenantProvider;
    }

    /** For request-scoped changes: tenant and actor come from the JWT. */
    public void record(String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        recordFor(currentTenantProvider.getCurrentTenantId(), currentTenantProvider.getCurrentUserId(), action,
                resourceType, resourceId, metadata);
    }

    /** For the scheduler (no security context): tenant is explicit, actor may be null. */
    public void recordFor(UUID tenantId, UUID actorUserId, String action, String resourceType, UUID resourceId,
            Map<String, Object> metadata) {
        String json;
        try {
            json = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            json = "{}";
        }
        jdbc.update(SQL, tenantId, actorUserId == null ? null : actorUserId.toString(), action, resourceType,
                resourceId == null ? null : resourceId.toString(), json);
    }
}
