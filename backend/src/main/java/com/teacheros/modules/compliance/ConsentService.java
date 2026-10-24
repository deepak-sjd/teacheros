package com.teacheros.modules.compliance;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.compliance.dto.ComplianceDtos.ChildConsent;
import com.teacheros.modules.compliance.dto.ComplianceDtos.ConsentStatusResponse;
import com.teacheros.modules.compliance.dto.ComplianceDtos.GrantConsentRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Guardian consent for processing a child's data. PARENT accounts only; scoped
 * to the parent's own children.
 */
@Service
public class ConsentService {

    static final String PURPOSE = "CHILD_DATA_PROCESSING";

    private final ConsentRecordRepository consentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;
    private final String currentTextVersion;

    public ConsentService(ConsentRecordRepository consentRepository, StudentRepository studentRepository,
            UserRepository userRepository, StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider, AuditService auditService,
            @Value("${app.compliance.consent-text-version:v1}") String currentTextVersion) {
        this.consentRepository = consentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
        this.currentTextVersion = currentTextVersion;
    }

    @Transactional(readOnly = true)
    public ConsentStatusResponse status() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        List<Student> children = myChildren(tenantId);
        Map<UUID, ConsentRecord> latest = latestByStudent(tenantId, children);

        List<ChildConsent> rows = children.stream().map(c -> {
            ConsentRecord r = latest.get(c.getId());
            String state = r == null ? "PENDING" : (r.getWithdrawnAt() == null ? "GRANTED" : "WITHDRAWN");
            return new ChildConsent(c.getId(), c.getFullName(), state,
                    r == null ? null : r.getTextVersion(), r == null ? null : r.getGrantedAt(),
                    r == null ? null : r.getWithdrawnAt());
        }).toList();

        // Only "never asked" blocks the app. A withdrawn child must not trap the parent
        // in a consent loop.
        boolean required = rows.stream().anyMatch(r -> r.state().equals("PENDING"));
        return new ConsentStatusResponse(required, currentTextVersion, rows);
    }

    @Transactional
    public ConsentStatusResponse grant(GrantConsentRequest request) {
        requireParent();
        if (!currentTextVersion.equals(request.textVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_TEXT_OUTDATED",
                    "The consent text has changed. Reload the page and review it again.");
        }
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String phone = currentUserPhone();
        List<Student> children = myChildren(tenantId);
        Map<UUID, ConsentRecord> latest = latestByStudent(tenantId, children);

        Set<UUID> target = request.studentIds() == null ? null : new HashSet<>(request.studentIds());
        if (target != null) {
            Set<UUID> mine = children.stream().map(Student::getId).collect(Collectors.toSet());
            for (UUID id : target) {
                if (!mine.contains(id)) {
                    throw new ResourceNotFoundException("Student", id); // someone else's child
                }
            }
        }

        for (Student child : children) {
            if (target != null && !target.contains(child.getId())) {
                continue;
            }
            ConsentRecord prior = latest.get(child.getId());
            boolean eligible = prior == null || (target != null && prior.getWithdrawnAt() != null);
            if (!eligible) {
                continue;
            }
            consentRepository.save(new ConsentRecord(tenantId, child.getId(), phone, PURPOSE, currentTextVersion));
            auditService.record(ComplianceAuditActions.CONSENT_GRANTED, ComplianceAuditActions.CONSENT,
                    child.getId(), AuditService.meta("studentId", child.getId(), "textVersion", currentTextVersion));
        }
        return status();
    }

    @Transactional
    public ConsentStatusResponse withdraw(UUID studentId) {
        requireParent();
        studentAccessGuard.assertCanView(studentId); // 404 for another parent's child
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        ConsentRecord active = consentRepository
                .findFirstByTenantIdAndStudentIdAndPurposeAndWithdrawnAtIsNull(tenantId, studentId, PURPOSE)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_ACTIVE_CONSENT",
                        "There is no active consent to withdraw for this child."));
        active.setWithdrawnAt(Instant.now());
        consentRepository.save(active);
        auditService.record(ComplianceAuditActions.CONSENT_WITHDRAWN, ComplianceAuditActions.CONSENT, studentId,
                AuditService.meta("studentId", studentId, "textVersion", active.getTextVersion()));
        return status();
    }

    private void requireParent() {
        if (!"PARENT".equals(currentTenantProvider.getCurrentRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PARENT_ONLY", "Only a guardian account can do this.");
        }
    }

    private List<Student> myChildren(UUID tenantId) {
        return studentRepository.findByTenantIdAndGuardianPhoneAndActiveTrue(tenantId, currentUserPhone());
    }

    private String currentUserPhone() {
        return userRepository.findById(currentTenantProvider.getCurrentUserId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getPhoneNumber();
    }

    private Map<UUID, ConsentRecord> latestByStudent(UUID tenantId, List<Student> children) {
        if (children.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = children.stream().map(Student::getId).toList();
        return consentRepository.findByTenantIdAndStudentIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(ConsentRecord::getStudentId, r -> r,
                        BinaryOperatorHolder.latest()));
    }

    private static final class BinaryOperatorHolder {
        static java.util.function.BinaryOperator<ConsentRecord> latest() {
            return (a, b) -> Comparator.comparing(ConsentRecord::getGrantedAt).compare(a, b) >= 0 ? a : b;
        }
    }
}
