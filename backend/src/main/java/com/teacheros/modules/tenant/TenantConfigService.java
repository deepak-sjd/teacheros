package com.teacheros.modules.tenant;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.tenant.dto.TenantDtos.TenantConfigResponse;
import com.teacheros.modules.tenant.dto.TenantDtos.UpdateTenantConfigRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The one place other modules read tenant type / enabled modules from:
 * TenantConfigService#get() for the current request's tenant,
 * #isModuleEnabled(key) for a quick check, #getFor(tenantId) for
 * background jobs that have no request context.
 */
@Service
public class TenantConfigService {

    private final TenantConfigRepository tenantConfigRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public TenantConfigService(TenantConfigRepository tenantConfigRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.tenantConfigRepository = tenantConfigRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public TenantConfigResponse get() {
        return toResponse(getOrCreate(currentTenantProvider.getCurrentTenantId()));
    }

    /**
     * For scheduled jobs only - request code must use get(), never a
     * client-supplied id.
     */
    @Transactional
    public TenantConfigResponse getFor(UUID tenantId) {
        return toResponse(getOrCreate(tenantId));
    }

    public boolean isModuleEnabled(String moduleKey) {
        return get().enabledModules().contains(moduleKey);
    }

    public ZoneId zone() {
        return ZoneId.of(get().timezone());
    }

    /**
     * Called at signup (AuthService.provisionNewTeacherAccount) inside the
     * same transaction that creates the tenant, so a tenant never exists
     * without a config row. Takes the id explicitly because there's no
     * authenticated principal yet at that point.
     */
    @Transactional
    public TenantConfigResponse createDefault(UUID tenantId) {
        return toResponse(tenantConfigRepository.save(newDefault(tenantId)));
    }

    @Transactional
    public TenantConfigResponse update(UpdateTenantConfigRequest request) {
        TenantConfig config = getOrCreate(currentTenantProvider.getCurrentTenantId());

        if (request.tenantType() != null) {
            config.setTenantType(parseType(request.tenantType()));
        }
        if (request.enabledModules() != null) {
            config.setEnabledModules(validateModules(request.enabledModules()));
        }
        if (request.locale() != null) {
            config.setLocale(request.locale());
        }
        if (request.timezone() != null) {
            config.setTimezone(validateZone(request.timezone()));
        }
        // null = unchanged, "" = clear
        if (request.primaryColor() != null) {
            config.setPrimaryColor(blankToNull(request.primaryColor()));
        }
        if (request.secondaryColor() != null) {
            config.setSecondaryColor(blankToNull(request.secondaryColor()));
        }
        return toResponse(tenantConfigRepository.save(config));
    }

    // Self-heal: the V20 backfill and signup hook mean a row should always
    // exist, but a read must never fail because one is missing.
    private TenantConfig getOrCreate(UUID tenantId) {
        return tenantConfigRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantConfigRepository.save(newDefault(tenantId)));
    }

    private TenantConfig newDefault(UUID tenantId) {
        return new TenantConfig(tenantId, TenantType.COACHING, TenantModules.defaultsFor(TenantType.COACHING));
    }

    private TenantType parseType(String value) {
        try {
            return TenantType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_TYPE",
                    "tenantType must be COACHING or SCHOOL");
        }
    }

    private List<String> validateModules(List<String> modules) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String m : modules) {
            String key = m == null ? "" : m.trim().toUpperCase();
            if (!TenantModules.ALL.contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_MODULE", "Unknown module: " + m);
            }
            cleaned.add(key);
        }
        return new ArrayList<>(cleaned);
    }

    private String validateZone(String zone) {
        try {
            return ZoneId.of(zone.trim()).getId();
        } catch (DateTimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Unknown timezone: " + zone);
        }
    }

    private String blankToNull(String s) {
        return s.isBlank() ? null : s.trim();
    }

    private TenantConfigResponse toResponse(TenantConfig c) {
        return new TenantConfigResponse(c.getTenantType(), List.copyOf(c.getEnabledModules()), c.getLocale(),
                c.getTimezone(), c.getPrimaryColor(), c.getSecondaryColor());
    }
}
