package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.dto.AcademicDtos.AcademicYearResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateAcademicYearRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateAcademicYearRequest;
import com.teacheros.modules.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AcademicYearService {

    private final AcademicYearRepository yearRepository;
    private final SchoolTenantGuard guard;
    private final CurrentTenantProvider tenantProvider;
    private final AuditService auditService;

    public AcademicYearService(AcademicYearRepository yearRepository, SchoolTenantGuard guard,
            CurrentTenantProvider tenantProvider, AuditService auditService) {
        this.yearRepository = yearRepository;
        this.guard = guard;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    public List<AcademicYearResponse> list() {
        guard.assertSchool();
        return yearRepository.findByTenantIdOrderByStartDateDesc(tenantProvider.getCurrentTenantId()).stream()
                .map(AcademicYearService::toResponse).toList();
    }

    @Transactional
    public AcademicYearResponse create(CreateAcademicYearRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        String name = request.name().trim();
        assertDates(request.startDate(), request.endDate());

        List<AcademicYear> existing = yearRepository.findByTenantIdOrderByStartDateDesc(tenantId);
        assertNameFree(existing, name, null);
        assertNoOverlap(existing, request.startDate(), request.endDate(), null);

        AcademicYear year = yearRepository.save(
                new AcademicYear(tenantId, name, request.startDate(), request.endDate()));
        if (Boolean.TRUE.equals(request.current()) || existing.isEmpty()) {
            makeCurrent(tenantId, year);
        }
        auditService.record("ACADEMIC_YEAR_CREATED", "ACADEMIC_YEAR", year.getId(),
                AuditService.meta("name", name, "current", year.isCurrent()));
        return toResponse(year);
    }

    @Transactional
    public AcademicYearResponse update(UUID id, UpdateAcademicYearRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        AcademicYear year = yearRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", id));
        String name = request.name().trim();
        assertDates(request.startDate(), request.endDate());

        List<AcademicYear> all = yearRepository.findByTenantIdOrderByStartDateDesc(tenantId);
        assertNameFree(all, name, id);
        assertNoOverlap(all, request.startDate(), request.endDate(), id);

        year.setName(name);
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        return toResponse(yearRepository.save(year));
    }

    /**
     * Idempotent: making the current year current again changes (and audits)
     * nothing.
     */
    @Transactional
    public AcademicYearResponse setCurrent(UUID id) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        AcademicYear year = yearRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", id));
        if (!year.isCurrent()) {
            makeCurrent(tenantId, year);
            auditService.record("ACADEMIC_YEAR_SET_CURRENT", "ACADEMIC_YEAR", year.getId(),
                    AuditService.meta("name", year.getName()));
        }
        return toResponse(year);
    }

    // The previous current year is cleared and flushed first - the partial
    // unique index allows only one current row per tenant at any moment.
    private void makeCurrent(UUID tenantId, AcademicYear year) {
        yearRepository.findByTenantIdAndCurrentTrue(tenantId)
                .filter(previous -> !previous.getId().equals(year.getId()))
                .ifPresent(previous -> {
                    previous.setCurrent(false);
                    yearRepository.saveAndFlush(previous);
                });
        year.setCurrent(true);
        yearRepository.save(year);
    }

    private void assertDates(LocalDate start, LocalDate end) {
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATES",
                    "The end date must be after the start date.");
        }
    }

    private void assertNameFree(List<AcademicYear> years, String name, UUID selfId) {
        boolean taken = years.stream()
                .anyMatch(y -> !y.getId().equals(selfId) && y.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "YEAR_EXISTS",
                    "An academic year named " + name + " already exists.");
        }
    }

    private void assertNoOverlap(List<AcademicYear> years, LocalDate start, LocalDate end, UUID selfId) {
        boolean overlaps = years.stream()
                .filter(y -> !y.getId().equals(selfId))
                .anyMatch(y -> !start.isAfter(y.getEndDate()) && !y.getStartDate().isAfter(end));
        if (overlaps) {
            throw new ApiException(HttpStatus.CONFLICT, "YEAR_OVERLAP", "Those dates overlap another academic year.");
        }
    }

    private static AcademicYearResponse toResponse(AcademicYear y) {
        return new AcademicYearResponse(y.getId(), y.getName(), y.getStartDate(), y.getEndDate(), y.isCurrent());
    }
}
