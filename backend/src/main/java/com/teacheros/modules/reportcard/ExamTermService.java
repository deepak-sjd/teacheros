package com.teacheros.modules.reportcard;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ExamTermRequest;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ExamTermResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExamTermService {

    private final ExamTermRepository examTermRepository;
    private final ReportCardRemarkRepository remarkRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public ExamTermService(ExamTermRepository examTermRepository, ReportCardRemarkRepository remarkRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.examTermRepository = examTermRepository;
        this.remarkRepository = remarkRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<ExamTermResponse> list() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return examTermRepository.findByTenantIdOrderByStartDateAsc(tenantId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ExamTermResponse create(ExamTermRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        validate(tenantId, null, request);
        ExamTerm term = new ExamTerm(tenantId, request.academicYearId(), request.name().trim(),
                request.startDate(), request.endDate());
        return toResponse(examTermRepository.save(term));
    }

    @Transactional
    public ExamTermResponse update(UUID termId, ExamTermRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        ExamTerm term = examTermRepository.findByIdAndTenantId(termId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam term", termId));
        validate(tenantId, termId, request);
        term.setName(request.name().trim());
        term.setAcademicYearId(request.academicYearId());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        return toResponse(examTermRepository.save(term));
    }

    /**
     * Removes the term and the teacher remarks entered against it. Marks are
     * untouched.
     */
    @Transactional
    public void delete(UUID termId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        ExamTerm term = examTermRepository.findByIdAndTenantId(termId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam term", termId));
        remarkRepository.deleteForTerm(tenantId, termId);
        examTermRepository.delete(term);
    }

    /**
     * Two terms in the same academic year must not overlap - a test dated in
     * the overlap would otherwise be counted in both. (Terms with no year set
     * are treated as one group.)
     */
    private void validate(UUID tenantId, UUID excludeId, ExamTermRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TERM_DATES",
                    "End date must not be before start date.");
        }
        boolean overlaps = examTermRepository.findByTenantIdOrderByStartDateAsc(tenantId).stream()
                .filter(t -> !t.getId().equals(excludeId))
                .filter(t -> Objects.equals(t.getAcademicYearId(), request.academicYearId()))
                .anyMatch(t -> !request.startDate().isAfter(t.getEndDate())
                        && !request.endDate().isBefore(t.getStartDate()));
        if (overlaps) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TERM_OVERLAP",
                    "These dates overlap another term in the same academic year.");
        }
    }

    private ExamTermResponse toResponse(ExamTerm t) {
        return new ExamTermResponse(t.getId(), t.getAcademicYearId(), t.getName(), t.getStartDate(),
                t.getEndDate());
    }
}
