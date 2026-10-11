package com.teacheros.modules.tenant;

import com.teacheros.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * The one place an unauthenticated request turns a PUBLIC school slug into a
 * tenant. Public routes have no JWT, so this is the sole exception to "tenant
 * scope comes from CurrentTenantProvider" - and it only ever accepts the slug,
 * never a tenant id from the client.
 */
@Component
public class PublicTenantResolver {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{1,80}$");

    private final TenantRepository tenantRepository;

    public PublicTenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant resolve(String rawSlug) {
        String slug = rawSlug == null ? "" : rawSlug.trim().toLowerCase();
        if (!SLUG.matcher(slug).matches()) {
            throw notFound();
        }
        return tenantRepository.findBySlugAndActiveTrue(slug).orElseThrow(PublicTenantResolver::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "SCHOOL_NOT_FOUND", "We couldn't find that school.");
    }
}
