package com.teacheros.common.tenant;

import com.teacheros.common.security.AuthenticatedPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * THE single source of truth for "which tenant is this request scoped to."
 *
 * This is the most security-critical class in the codebase. Every service
 * method that reads or writes tenant-owned data MUST obtain the tenant ID
 * from here - never from a request parameter, request body field, or path
 * variable. The tenant ID always comes from the authenticated principal,
 * which was itself derived from a verified JWT.
 *
 * If you find yourself tempted to accept a tenantId from the client to
 * "make testing easier," don't. That is exactly the class of bug that
 * causes cross-tenant data leaks.
 */
@Component
public class CurrentTenantProvider {

    public UUID getCurrentTenantId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            return authenticatedPrincipal.tenantId();
        }
        throw new IllegalStateException(
                "No authenticated tenant context available. This method must only be called " +
                        "within an authenticated request.");
    }

    public UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            return authenticatedPrincipal.userId();
        }
        throw new IllegalStateException(
                "No authenticated user context available. This method must only be called " +
                        "within an authenticated request.");
    }

    public String getCurrentRole() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedPrincipal authenticatedPrincipal) {
            return authenticatedPrincipal.role();
        }
        throw new IllegalStateException(
                "No authenticated role available. This method must only be called " +
                        "within an authenticated request.");
    }
}
