package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.tenant.TenantConfigService;
import com.teacheros.modules.tenant.TenantType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Academic structure only exists for SCHOOL tenants. Every academic
 * service method starts with assertSchool(), so a COACHING tenant gets a
 * clear 403 and none of their existing behaviour changes.
 */
@Component
public class SchoolTenantGuard {

    private final TenantConfigService tenantConfigService;

    public SchoolTenantGuard(TenantConfigService tenantConfigService) {
        this.tenantConfigService = tenantConfigService;
    }

    public void assertSchool() {
        if (tenantConfigService.get().tenantType() != TenantType.SCHOOL) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SCHOOL_ONLY",
                    "Classes and sections are only available for schools.");
        }
    }
}
