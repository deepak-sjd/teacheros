package com.teacheros.common.security;

import java.util.UUID;

/**
 * What we put into the Spring Security context after a JWT is validated.
 * Deliberately minimal - just enough to authorize requests and resolve
 * tenant scope. Do not add PII here; fetch it from the database in the
 * service layer if a handler actually needs it.
 */
public record AuthenticatedPrincipal(
        UUID userId,
        UUID tenantId,
        String role
) {
}
