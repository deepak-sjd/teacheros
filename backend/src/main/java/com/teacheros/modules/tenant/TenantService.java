package com.teacheros.modules.tenant;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.tenant.dto.TenantDtos.TenantSettingsResponse;
import com.teacheros.modules.tenant.dto.TenantDtos.UpdateTenantRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
public class TenantService {

    // Images only, same reasoning as material.AllowedContentTypes (declared-
    // type check, not deep content sniffing - adequate for MVP scope). Not
    // reusing that class since it's package-private to modules.material and
    // includes types (PDF, Word docs) that make no sense for a logo.
    private static final Set<String> ALLOWED_LOGO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final TenantRepository tenantRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final StorageService storageService;

    public TenantService(TenantRepository tenantRepository, CurrentTenantProvider currentTenantProvider,
            StorageService storageService) {
        this.tenantRepository = tenantRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.storageService = storageService;
    }

    public TenantSettingsResponse getSettings() {
        return toResponse(currentTenant());
    }

    @Transactional
    public TenantSettingsResponse updateSettings(UpdateTenantRequest request) {
        Tenant tenant = currentTenant();
        tenant.setName(request.name());
        tenant = tenantRepository.save(tenant);
        return toResponse(tenant);
    }

    @Transactional
    public TenantSettingsResponse uploadLogo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "No file was uploaded");
        }
        if (!ALLOWED_LOGO_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                    "Logo must be a PNG, JPEG, or WebP image");
        }

        Tenant tenant = currentTenant();
        String oldKey = tenant.getLogoObjectKey();

        String newKey = storageService.uploadTenantAsset(tenant.getId(), file);
        tenant.setLogoObjectKey(newKey);
        tenant = tenantRepository.save(tenant);

        // Delete the old file only after the new one is safely saved and
        // committed to the row - deleting first and having the upload fail
        // would leave the tenant with no logo at all.
        if (oldKey != null) {
            storageService.delete(oldKey);
        }

        return toResponse(tenant);
    }

    @Transactional
    public TenantSettingsResponse deleteLogo() {
        Tenant tenant = currentTenant();
        if (tenant.getLogoObjectKey() != null) {
            storageService.delete(tenant.getLogoObjectKey());
            tenant.setLogoObjectKey(null);
            tenant = tenantRepository.save(tenant);
        }
        return toResponse(tenant);
    }

    private Tenant currentTenant() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private TenantSettingsResponse toResponse(Tenant tenant) {
        String logoUrl = tenant.getLogoObjectKey() == null
                ? null
                : storageService.generateDownloadUrl(tenant.getLogoObjectKey());
        return new TenantSettingsResponse(tenant.getId(), tenant.getName(), logoUrl);
    }
}
