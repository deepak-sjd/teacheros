package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.CreateFeeHeadRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.FeeHeadResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.UpdateFeeHeadRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FeeHeadService {

    private final FeeHeadRepository feeHeadRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeHeadService(FeeHeadRepository feeHeadRepository, CurrentTenantProvider currentTenantProvider) {
        this.feeHeadRepository = feeHeadRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<FeeHeadResponse> list() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return feeHeadRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public FeeHeadResponse create(CreateFeeHeadRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String name = request.name().trim();
        if (feeHeadRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "FEE_HEAD_EXISTS", "A fee head named \"" + name + "\" already exists.");
        }
        FeeHead head = new FeeHead(tenantId, name, blankToNull(request.code()),
                request.siblingConcessionEligible() == null || request.siblingConcessionEligible(),
                request.sortOrder() == null ? 0 : request.sortOrder());
        return toResponse(feeHeadRepository.save(head));
    }

    @Transactional
    public FeeHeadResponse update(UUID id, UpdateFeeHeadRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        FeeHead head = feeHeadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee head", id));
        String name = request.name().trim();
        if (feeHeadRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "FEE_HEAD_EXISTS", "A fee head named \"" + name + "\" already exists.");
        }
        head.setName(name);
        head.setCode(blankToNull(request.code()));
        head.setSiblingConcessionEligible(request.siblingConcessionEligible());
        head.setActive(request.active());
        head.setSortOrder(request.sortOrder());
        return toResponse(feeHeadRepository.save(head));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private FeeHeadResponse toResponse(FeeHead h) {
        return new FeeHeadResponse(h.getId(), h.getName(), h.getCode(), h.isSiblingConcessionEligible(),
                h.isActive(), h.getSortOrder());
    }
}
