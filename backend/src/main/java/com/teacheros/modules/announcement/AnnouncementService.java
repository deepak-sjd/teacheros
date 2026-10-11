package com.teacheros.modules.announcement;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.AnnouncementResponse;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.FeedAnnouncementResponse;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.ReadStatsResponse;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff create/list/stats; PARENT/STUDENT feed + mark-read.
 *
 * Parent/student visibility is derived from their linked students - the same
 * linkage StudentAccessGuard/BatchAccessGuard use (guardian_phone for a
 * PARENT, the student's own phone_number for a STUDENT). A caller with no
 * linked students only ever sees ALL announcements.
 */
@Service
public class AnnouncementService {

    // IN (...) needs at least one element; these never match real data.
    private static final UUID NO_BATCH = new UUID(0L, 0L);
    // Placeholder for "this student has no grade" in the IN (...) list. It only
    // needs to match
    // nothing. Not \u0000: Postgres rejects NUL bytes in strings. The query
    // compares against
    // lower(trim(grade)), so keep it lowercase and padded with underscores so no
    // real grade equals it.
    private static final String NO_GRADE = "__no_grade__";

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            AnnouncementReadRepository announcementReadRepository,
            BatchRepository batchRepository,
            StudentRepository studentRepository,
            UserRepository userRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    // ---- Staff ----------------------------------------------------------

    @Transactional
    public AnnouncementResponse create(CreateAnnouncementRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        UUID batchId = null;
        String grade = null;
        if (request.audience() == AnnouncementAudience.BATCH) {
            if (request.batchId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_REQUIRED",
                        "Choose a batch for a batch announcement.");
            }
            batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));
            batchId = request.batchId();
        } else if (request.audience() == AnnouncementAudience.CLASS) {
            if (request.grade() == null || request.grade().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GRADE_REQUIRED",
                        "Enter a grade/class for a class announcement.");
            }
            grade = request.grade().trim();
        }

        String creatorName = userRepository.findById(userId).map(User::getDisplayName).orElse("School");

        Announcement saved = announcementRepository.save(new Announcement(tenantId, request.title().trim(),
                request.body().trim(), request.audience(), batchId, grade, userId, creatorName));
        return toResponse(saved);
    }

    public Page<AnnouncementResponse> listForStaff(Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return announcementRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(this::toResponse);
    }

    public ReadStatsResponse readStats(UUID announcementId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Announcement announcement = announcementRepository.findByIdAndTenantId(announcementId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", announcementId));

        Set<String> recipientPhones = new HashSet<>();
        for (Student s : targetStudents(tenantId, announcement)) {
            if (s.getGuardianPhone() != null && !s.getGuardianPhone().isBlank()) {
                recipientPhones.add(s.getGuardianPhone());
            }
            if (s.getPhoneNumber() != null && !s.getPhoneNumber().isBlank()) {
                recipientPhones.add(s.getPhoneNumber());
            }
        }

        List<UUID> readerIds = announcementReadRepository
                .findByTenantIdAndAnnouncementId(tenantId, announcementId).stream()
                .map(AnnouncementRead::getUserId)
                .toList();
        Set<String> readerPhones = userRepository.findAllById(readerIds).stream()
                .map(User::getPhoneNumber)
                .collect(Collectors.toSet());

        long readCount = readerPhones.stream().filter(recipientPhones::contains).count();
        return new ReadStatsResponse(announcementId, readCount, recipientPhones.size());
    }

    // ---- Parent / student -----------------------------------------------

    public Page<FeedAnnouncementResponse> feed(Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        CallerAudience audience = resolveCallerAudience(tenantId);

        Page<Announcement> page = announcementRepository.findVisible(tenantId,
                audience.batchIds().isEmpty() ? Set.of(NO_BATCH) : audience.batchIds(),
                audience.gradesLower().isEmpty() ? Set.of(NO_GRADE) : audience.gradesLower(),
                pageable);

        Set<UUID> readIds = page.isEmpty()
                ? Set.of()
                : announcementReadRepository
                        .findByTenantIdAndUserIdAndAnnouncementIdIn(tenantId, userId,
                                page.getContent().stream().map(Announcement::getId).toList())
                        .stream().map(AnnouncementRead::getAnnouncementId).collect(Collectors.toSet());

        return page.map(a -> toFeed(a, readIds.contains(a.getId())));
    }

    @Transactional
    public void markRead(UUID announcementId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        Announcement announcement = announcementRepository.findByIdAndTenantId(announcementId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", announcementId));

        CallerAudience audience = resolveCallerAudience(tenantId);
        if (!isVisibleTo(announcement, audience.batchIds(), audience.gradesLower())) {
            // 404 not 403, same as the access guards - don't confirm it exists.
            throw new ResourceNotFoundException("Announcement", announcementId);
        }
        announcementReadRepository.markRead(tenantId, announcementId, userId);
    }

    // ---- helpers --------------------------------------------------------

    static boolean isVisibleTo(Announcement a, Set<UUID> batchIds, Set<String> gradesLower) {
        return switch (a.getAudience()) {
            case ALL -> true;
            case BATCH -> a.getBatchId() != null && batchIds.contains(a.getBatchId());
            case CLASS -> a.getGrade() != null && gradesLower.contains(a.getGrade().trim().toLowerCase(Locale.ROOT));
        };
    }

    private record CallerAudience(Set<UUID> batchIds, Set<String> gradesLower) {
    }

    private CallerAudience resolveCallerAudience(UUID tenantId) {
        String role = currentTenantProvider.getCurrentRole();
        User user = userRepository.findById(currentTenantProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", currentTenantProvider.getCurrentUserId()));

        List<Student> linked = switch (role) {
            case "PARENT" -> studentRepository.findByTenantIdAndGuardianPhoneAndActiveTrue(
                    tenantId, user.getPhoneNumber());
            case "STUDENT" -> studentRepository.findFirstByTenantIdAndPhoneNumberAndActiveTrue(
                    tenantId, user.getPhoneNumber()).map(List::of).orElse(List.of());
            default -> List.of();
        };

        Set<UUID> batchIds = linked.stream().map(Student::getBatchId)
                .filter(b -> b != null).collect(Collectors.toSet());
        Set<String> grades = linked.stream().map(Student::getGrade)
                .filter(g -> g != null && !g.isBlank())
                .map(g -> g.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return new CallerAudience(batchIds, grades);
    }

    private List<Student> targetStudents(UUID tenantId, Announcement a) {
        return switch (a.getAudience()) {
            case ALL -> studentRepository.findAllByTenantIdAndActiveTrue(tenantId);
            case BATCH -> studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, a.getBatchId());
            case CLASS -> studentRepository.findAllByTenantIdAndActiveTrue(tenantId).stream()
                    .filter(s -> s.getGrade() != null && s.getGrade().trim().equalsIgnoreCase(a.getGrade().trim()))
                    .toList();
        };
    }

    private AnnouncementResponse toResponse(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getAudience(), a.getBatchId(),
                a.getGrade(), a.getCreatedByName(), a.getCreatedAt());
    }

    private FeedAnnouncementResponse toFeed(Announcement a, boolean read) {
        return new FeedAnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getAudience(), a.getBatchId(),
                a.getGrade(), a.getCreatedByName(), a.getCreatedAt(), read);
    }
}
