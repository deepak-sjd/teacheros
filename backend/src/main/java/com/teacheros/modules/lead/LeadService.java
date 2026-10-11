package com.teacheros.modules.lead;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.lead.dto.LeadDtos.AssigneeResponse;
import com.teacheros.modules.lead.dto.LeadDtos.AssignLeadRequest;
import com.teacheros.modules.lead.dto.LeadDtos.ConvertLeadRequest;
import com.teacheros.modules.lead.dto.LeadDtos.ConvertLeadResponse;
import com.teacheros.modules.lead.dto.LeadDtos.FollowUpRequest;
import com.teacheros.modules.lead.dto.LeadDtos.LeadCountsResponse;
import com.teacheros.modules.lead.dto.LeadDtos.LeadDetailResponse;
import com.teacheros.modules.lead.dto.LeadDtos.LeadHistoryEntry;
import com.teacheros.modules.lead.dto.LeadDtos.LeadResponse;
import com.teacheros.modules.lead.dto.LeadDtos.UpdateLeadStatusRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff-side lead pipeline. Every method takes the tenant from
 * CurrentTenantProvider and every lookup is tenant-scoped. The public
 * (unauthenticated) intake lives in PublicEnquiryService and can only
 * create NEW leads - it has no read path.
 */
@Service
public class LeadService {

    private static final List<Role> STAFF_ROLES = List.of(Role.OWNER, Role.TEACHER, Role.STAFF, Role.ADMIN);

    private final LeadRepository leadRepository;
    private final LeadStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public LeadService(
            LeadRepository leadRepository,
            LeadStatusHistoryRepository historyRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.leadRepository = leadRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public Page<LeadResponse> list(LeadStatus status, UUID assignedTo, Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Page<Lead> page = leadRepository.search(tenantId, status, assignedTo, pageable);
        Map<UUID, String> names = userNames(tenantId,
                page.getContent().stream().map(Lead::getAssignedTo).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
        return page.map(l -> toResponse(l, names));
    }

    public LeadCountsResponse counts() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus s : LeadStatus.values()) {
            counts.put(s, 0L);
        }
        leadRepository.countByStatus(tenantId).forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return new LeadCountsResponse(counts);
    }

    public List<AssigneeResponse> listAssignees() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return userRepository.findByTenantIdAndActiveTrueAndRoleIn(tenantId, STAFF_ROLES).stream()
                .map(u -> new AssigneeResponse(u.getId(), u.getDisplayName(), u.getRole()))
                .toList();
    }

    public LeadDetailResponse get(UUID leadId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lead lead = require(tenantId, leadId);

        List<LeadStatusHistory> history = historyRepository.findByTenantIdAndLeadIdOrderByChangedAtAsc(tenantId,
                leadId);
        Set<UUID> userIds = history.stream().map(LeadStatusHistory::getChangedByUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (lead.getAssignedTo() != null)
            userIds.add(lead.getAssignedTo());
        Map<UUID, String> names = userNames(tenantId, userIds);

        List<LeadHistoryEntry> entries = history.stream()
                .map(h -> new LeadHistoryEntry(h.getId(), h.getFromStatus(), h.getToStatus(), h.getNote(),
                        h.getChangedByUserId() == null ? null : names.get(h.getChangedByUserId()), h.getChangedAt()))
                .toList();
        return new LeadDetailResponse(toResponse(lead, names), entries);
    }

    @Transactional
    public LeadResponse assign(UUID leadId, AssignLeadRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lead lead = require(tenantId, leadId);

        String note;
        if (request.userId() == null) {
            lead.setAssignedTo(null);
            note = "Unassigned";
        } else {
            User assignee = userRepository.findById(request.userId())
                    .filter(u -> tenantId.equals(u.getTenantId()) && u.isActive() && STAFF_ROLES.contains(u.getRole()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEE",
                            "That person can't be assigned leads."));
            lead.setAssignedTo(assignee.getId());
            note = "Assigned to " + assignee.getDisplayName();
        }
        leadRepository.save(lead);
        recordHistory(tenantId, lead.getId(), lead.getStatus(), lead.getStatus(), note);
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse updateStatus(UUID leadId, UpdateLeadStatusRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lead lead = require(tenantId, leadId);

        if (lead.getStatus() == LeadStatus.ENROLLED) {
            throw new ApiException(HttpStatus.CONFLICT, "LEAD_ALREADY_ENROLLED", "This lead is already enrolled.");
        }
        if (request.status() == LeadStatus.ENROLLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USE_CONVERT",
                    "Use \"Convert to student\" to enrol a lead.");
        }
        if (request.status() == lead.getStatus()) {
            return toResponse(lead);
        }

        LeadStatus from = lead.getStatus();
        lead.setStatus(request.status());
        if (request.status() == LeadStatus.LOST) {
            lead.setNextFollowUp(null);
        }
        leadRepository.save(lead);
        recordHistory(tenantId, lead.getId(), from, request.status(), blankToNull(request.note()));
        return toResponse(lead);
    }

    @Transactional
    public LeadResponse addFollowUp(UUID leadId, FollowUpRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lead lead = require(tenantId, leadId);

        if (lead.getStatus() == LeadStatus.ENROLLED || lead.getStatus() == LeadStatus.LOST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEAD_CLOSED",
                    "This lead is closed - reopen it before scheduling a follow-up.");
        }
        lead.setNextFollowUp(request.nextFollowUp());
        leadRepository.save(lead);

        String note = "Follow-up set for " + request.nextFollowUp()
                + (blankToNull(request.note()) != null ? ": " + request.note().trim() : "");
        recordHistory(tenantId, lead.getId(), lead.getStatus(), lead.getStatus(), truncate(note, 500));
        return toResponse(lead);
    }

    /**
     * Creates the Student from the lead - guardian phone is the lead's phone,
     * so the parent can log in with it (see AuthService.provisionAccountForPhone) -
     * and closes the lead as ENROLLED.
     */
    @Transactional
    public ConvertLeadResponse convert(UUID leadId, ConvertLeadRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Lead lead = require(tenantId, leadId);

        if (lead.getStatus() == LeadStatus.ENROLLED) {
            throw new ApiException(HttpStatus.CONFLICT, "LEAD_ALREADY_ENROLLED", "This lead is already enrolled.");
        }
        if (request.batchId() != null) {
            batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));
        }

        String studentName = blankToNull(request.studentName()) != null ? request.studentName().trim() : lead.getName();
        Student student = new Student(tenantId, request.batchId(), studentName, blankToNull(request.grade()),
                lead.getPhone());
        student.setGuardianEmail(lead.getEmail());
        student = studentRepository.save(student);

        LeadStatus from = lead.getStatus();
        lead.setStatus(LeadStatus.ENROLLED);
        lead.setConvertedStudentId(student.getId());
        lead.setNextFollowUp(null);
        leadRepository.save(lead);
        recordHistory(tenantId, lead.getId(), from, LeadStatus.ENROLLED, "Converted to student " + studentName);

        return new ConvertLeadResponse(toResponse(lead), student.getId());
    }

    // ---- helpers ----

    private Lead require(UUID tenantId, UUID leadId) {
        return leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", leadId));
    }

    private void recordHistory(UUID tenantId, UUID leadId, LeadStatus from, LeadStatus to, String note) {
        historyRepository.save(new LeadStatusHistory(tenantId, leadId, from, to, note,
                currentTenantProvider.getCurrentUserId()));
    }

    private Map<UUID, String> userNames(UUID tenantId, Collection<UUID> ids) {
        if (ids.isEmpty())
            return Map.of();
        return userRepository.findAllById(ids).stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    private LeadResponse toResponse(Lead lead) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Set<UUID> ids = lead.getAssignedTo() == null ? Set.of() : Set.of(lead.getAssignedTo());
        return toResponse(lead, userNames(tenantId, ids));
    }

    private LeadResponse toResponse(Lead l, Map<UUID, String> names) {
        return new LeadResponse(l.getId(), l.getName(), l.getPhone(), l.getEmail(), l.getMessage(), l.getSource(),
                l.getStatus(), l.getAssignedTo(), l.getAssignedTo() == null ? null : names.get(l.getAssignedTo()),
                l.getNextFollowUp(), l.getConvertedStudentId(), l.getCreatedAt(), l.getUpdatedAt());
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
