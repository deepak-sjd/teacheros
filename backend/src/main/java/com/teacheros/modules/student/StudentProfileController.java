package com.teacheros.modules.student;

import com.teacheros.modules.student.dto.StudentProfileDtos.DocumentDownloadUrlResponse;
import com.teacheros.modules.student.dto.StudentProfileDtos.StudentDocumentResponse;
import com.teacheros.modules.student.dto.StudentProfileDtos.StudentProfileResponse;
import com.teacheros.modules.student.dto.StudentProfileDtos.UpdateStudentProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Sibling to StudentController (same /api/v1/students base) - kept separate
 * so StudentController and its list endpoints stay untouched. Reads have no
 * 
 * @PreAuthorize: PARENT/STUDENT must reach their own child's/own data, and
 *                StudentProfileService enforces that via StudentAccessGuard
 *                (404 for
 *                anyone else). Writes are staff-only.
 */
@RestController
@RequestMapping("/api/v1/students/{studentId}")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentProfileResponse> getProfile(@PathVariable UUID studentId) {
        return ResponseEntity.ok(studentProfileService.getProfile(studentId));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<StudentProfileResponse> updateProfile(@PathVariable UUID studentId,
            @Valid @RequestBody UpdateStudentProfileRequest request) {
        return ResponseEntity.ok(studentProfileService.updateProfile(studentId, request));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<StudentDocumentResponse>> listDocuments(@PathVariable UUID studentId) {
        return ResponseEntity.ok(studentProfileService.listDocuments(studentId));
    }

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<StudentDocumentResponse> uploadDocument(
            @PathVariable UUID studentId,
            @RequestParam String docType,
            @RequestParam String title,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(studentProfileService.uploadDocument(studentId, docType, title, file));
    }

    @GetMapping("/documents/{documentId}/download-url")
    public ResponseEntity<DocumentDownloadUrlResponse> getDownloadUrl(@PathVariable UUID studentId,
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(studentProfileService.getDownloadUrl(studentId, documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID studentId, @PathVariable UUID documentId) {
        studentProfileService.deleteDocument(studentId, documentId);
        return ResponseEntity.noContent().build();
    }
}
