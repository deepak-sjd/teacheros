package com.teacheros.modules.reportcard;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeBandInput;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeBandResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.GradeScaleResponse;
import com.teacheros.modules.reportcard.dto.ReportCardDtos.ReplaceGradeScaleRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-tenant grade boundaries. A tenant that has never saved a scale gets
 * CBSE-style defaults (held here, not inserted into the table - a GET never
 * writes). Saving persists the tenant's own bands; deleting returns it to
 * the defaults.
 *
 * Lookup rule (see #resolve): a percentage gets the band with the highest
 * min_percentage that is <= the percentage. max_percentage is validated on
 * save but not used for lookup, so there are no gaps for scores like 90.5
 * between an "81-90" and a "91-100" band.
 */
@Service
public class GradeScaleService {

    // Highest-first, the order #resolve relies on.
    private static final List<GradeBandResponse> DEFAULT_BANDS = List.of(
            band("A1", 91, 100, "Outstanding"),
            band("A2", 81, 90, "Excellent"),
            band("B1", 71, 80, "Very good"),
            band("B2", 61, 70, "Good"),
            band("C1", 51, 60, "Above average"),
            band("C2", 41, 50, "Average"),
            band("D", 33, 40, "Needs improvement"),
            band("E1", 21, 32, "Below pass mark"),
            band("E2", 0, 20, "Below pass mark"));

    private final GradeScaleRepository gradeScaleRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public GradeScaleService(GradeScaleRepository gradeScaleRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.gradeScaleRepository = gradeScaleRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public GradeScaleResponse get() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        boolean usingDefaults = gradeScaleRepository.findByTenantIdOrderByMinPercentageDesc(tenantId).isEmpty();
        return new GradeScaleResponse(usingDefaults, effectiveBands(tenantId));
    }

    @Transactional
    public GradeScaleResponse replace(ReplaceGradeScaleRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        validate(request.bands());

        gradeScaleRepository.deleteForTenant(tenantId);
        gradeScaleRepository.saveAll(request.bands().stream()
                .map(b -> new GradeScale(tenantId, b.grade().trim(), b.minPercentage(), b.maxPercentage(),
                        b.remark() == null || b.remark().isBlank() ? null : b.remark().trim()))
                .toList());
        return get();
    }

    /** Back to the CBSE-style defaults. */
    @Transactional
    public GradeScaleResponse reset() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        gradeScaleRepository.deleteForTenant(tenantId);
        return get();
    }

    /**
     * The tenant's own bands if it has saved any, else the defaults. Highest-first.
     */
    public List<GradeBandResponse> effectiveBands(UUID tenantId) {
        List<GradeScale> rows = gradeScaleRepository.findByTenantIdOrderByMinPercentageDesc(tenantId);
        if (rows.isEmpty()) {
            return DEFAULT_BANDS;
        }
        return rows.stream()
                .map(r -> new GradeBandResponse(r.getGrade(), r.getMinPercentage(), r.getMaxPercentage(),
                        r.getRemark()))
                .toList();
    }

    /**
     * @param bandsHighestFirst as returned by #effectiveBands. Null if nothing
     *                          matches.
     */
    public static GradeBandResponse resolve(List<GradeBandResponse> bandsHighestFirst, double percentage) {
        for (GradeBandResponse b : bandsHighestFirst) {
            if (percentage >= b.minPercentage().doubleValue()) {
                return b;
            }
        }
        return null;
    }

    private void validate(List<GradeBandInput> bands) {
        List<GradeBandInput> sorted = bands.stream()
                .sorted(Comparator.comparing(GradeBandInput::minPercentage)).toList();
        Set<String> seen = new HashSet<>();

        for (GradeBandInput b : sorted) {
            if (b.minPercentage().compareTo(b.maxPercentage()) > 0) {
                throw invalid("Grade " + b.grade().trim() + ": the minimum can't be above the maximum.");
            }
            if (!seen.add(b.grade().trim().toLowerCase())) {
                throw invalid("Grade " + b.grade().trim() + " appears more than once.");
            }
        }
        if (sorted.get(0).minPercentage().compareTo(BigDecimal.ZERO) != 0) {
            throw invalid("The lowest band must start at 0%.");
        }
        if (sorted.get(sorted.size() - 1).maxPercentage().compareTo(BigDecimal.valueOf(100)) != 0) {
            throw invalid("The highest band must end at 100%.");
        }
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).minPercentage().compareTo(sorted.get(i - 1).maxPercentage()) <= 0) {
                throw invalid("Grades " + sorted.get(i - 1).grade().trim() + " and "
                        + sorted.get(i).grade().trim() + " overlap.");
            }
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRADE_SCALE", message);
    }

    private static GradeBandResponse band(String grade, int min, int max, String remark) {
        return new GradeBandResponse(grade, BigDecimal.valueOf(min), BigDecimal.valueOf(max), remark);
    }
}
