package com.teacheros.modules.student;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.student.dto.StudentProfileDtos.DocumentDownloadUrlResponse;
import com.teacheros.modules.student.dto.StudentProfileDtos.StudentDocumentResponse;
import com.teacheros.modules.student.dto.StudentProfileDtos.StudentProfileResponse;
import com.teacheros.modules.student.dto.StudentProfileDtos.UpdateStudentProfileRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Profile extras and documents for a student. Every method starts with
 * StudentAccessGuard#assertCanView, same as the other per-student services:
 * staff reach any student in their tenant, a PARENT/STUDENT only their own
 * child / own record (404 otherwise).
 *
 * Medical notes are the sensitive part. They are only ever returned by
 * getProfile (never by StudentService's list endpoints), and only to staff
 * or a PARENT who passed the guard - i.e. the student's own guardian. They
 * are deliberately not logged anywhere in this class.
 *
 * Writes (profile edit, document upload/delete) are staff-only, enforced
 * at the controller via @PreAuthorize.
 */
@Service
public class StudentProfileService {

    // Documents, not study material: PDF and images only (no Word/text).
    private static final Set<String> ALLOWED_DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp");

    static final Set<String> DOCUMENT_CATEGORIES = Set.of(
            "BIRTH_CERTIFICATE", "TRANSFER_CERTIFICATE", "REPORT_CARD", "ID_PROOF", "MEDICAL", "PHOTO", "OTHER");

    private static final int DOWNLOAD_URL_MINUTES = 15; // matches StorageService#generateDownloadUrl

    private final StudentRepository studentRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StorageService storageService;
    private final CurrentTenantProvider currentTenantProvider;
    private final long maxFileSizeBytes;

    public StudentProfileService(
            StudentRepository studentRepository,
            StudentDocumentRepository studentDocumentRepository,
            StudentAccessGuard studentAccessGuard,
            StorageService storageService,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.storage.max-file-size-mb}") long maxFileSizeMb) {
        this.studentRepository = studentRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.storageService = storageService;
        this.currentTenantProvider = currentTenantProvider;
        this.maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;
    }

    // ---- Profile --------------------------------------------------------

    public StudentProfileResponse getProfile(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        Student student = requireStudent(studentId);
        return toProfileResponse(student, canSeeMedicalNotes(currentTenantProvider.getCurrentRole()));
    }

    @Transactional
    public StudentProfileResponse updateProfile(UUID studentId, UpdateStudentProfileRequest request) {
        // Staff-only at the controller; the guard is still called so every
        // method in this class follows the same first-line rule.
        studentAccessGuard.assertCanView(studentId);
        Student student = requireStudent(studentId);

        student.setPreviousSchool(blankToNull(request.previousSchool()));
        student.setMedicalNotes(blankToNull(request.medicalNotes()));
        student.setBloodGroup(blankToNull(request.bloodGroup()));
        student.setAddress(blankToNull(request.address()));
        student.setGuardian2Name(blankToNull(request.guardian2Name()));
        student.setGuardian2Phone(blankToNull(request.guardian2Phone()));
        student.setGuardian2Relation(blankToNull(request.guardian2Relation()));
        student = studentRepository.save(student);

        return toProfileResponse(student, true);
    }

    // ---- Documents ------------------------------------------------------

    public List<StudentDocumentResponse> listDocuments(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        requireStudent(studentId);
        return studentDocumentRepository.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId)
                .stream().map(this::toDocumentResponse).toList();
    }

    @Transactional
    public StudentDocumentResponse uploadDocument(UUID studentId, String docType, String title, MultipartFile file) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        requireStudent(studentId);

        if (title == null || title.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TITLE_REQUIRED", "Give this document a title.");
        }
        if (docType == null || !DOCUMENT_CATEGORIES.contains(docType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_TYPE",
                    "Choose a valid document type.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE",
                    "That file is too large. Maximum size is " + (maxFileSizeBytes / (1024 * 1024)) + "MB.");
        }
        if (file.getContentType() == null || !ALLOWED_DOCUMENT_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                    "That file type isn't supported. Upload a PDF or an image (PNG, JPEG, WebP).");
        }

        String objectKey = storageService.uploadStudentDocument(tenantId, studentId, file);

        StudentDocument document = new StudentDocument(tenantId, studentId, docType, title.trim(), objectKey,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file",
                file.getContentType(), file.getSize(), userId);
        return toDocumentResponse(studentDocumentRepository.save(document));
    }

    public DocumentDownloadUrlResponse getDownloadUrl(UUID studentId, UUID documentId) {
        studentAccessGuard.assertCanView(studentId);
        StudentDocument document = requireDocument(studentId, documentId);
        return new DocumentDownloadUrlResponse(
                storageService.generateDownloadUrl(document.getObjectKey()), DOWNLOAD_URL_MINUTES);
    }

    @Transactional
    public void deleteDocument(UUID studentId, UUID documentId) {
        studentAccessGuard.assertCanView(studentId);
        StudentDocument document = requireDocument(studentId, documentId);

        // Stored object first - if this fails the DB row (and the error)
        // stays visible instead of silently orphaning a file. Same order as
        // StudyMaterialService#delete.
        storageService.delete(document.getObjectKey());
        studentDocumentRepository.delete(document);
    }

    // ---- helpers --------------------------------------------------------

    private Student requireStudent(UUID studentId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
    }

    private StudentDocument requireDocument(UUID studentId, UUID documentId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return studentDocumentRepository.findByIdAndTenantIdAndStudentId(documentId, tenantId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student document", documentId));
    }

    // Staff, or a PARENT (who already passed the guard, so is this
    // student's own guardian). A STUDENT account does not see its own
    // medical notes - see the PR notes; easy to widen if the product owner
    // wants that.
    private boolean canSeeMedicalNotes(String role) {
        return isStaffRole(role) || "PARENT".equals(role);
    }

    private boolean isStaffRole(String role) {
        return switch (role) {
            case "OWNER", "TEACHER", "STAFF", "ADMIN" -> true;
            default -> false;
        };
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private StudentProfileResponse toProfileResponse(Student s, boolean includeMedicalNotes) {
        return new StudentProfileResponse(s.getId(), s.getPreviousSchool(),
                includeMedicalNotes ? s.getMedicalNotes() : null, includeMedicalNotes,
                s.getBloodGroup(), s.getAddress(), s.getGuardian2Name(), s.getGuardian2Phone(),
                s.getGuardian2Relation());
    }

    private StudentDocumentResponse toDocumentResponse(StudentDocument d) {
        return new StudentDocumentResponse(d.getId(), d.getStudentId(), d.getDocType(), d.getTitle(),
                d.getOriginalFilename(), d.getContentType(), d.getFileSizeBytes(), d.getCreatedAt());
    }
}
