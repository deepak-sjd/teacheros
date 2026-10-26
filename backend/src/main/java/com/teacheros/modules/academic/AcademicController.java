package com.teacheros.modules.academic;

import com.teacheros.modules.academic.dto.AcademicDtos.AcademicYearResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.AssignTeacherRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.ClassResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateAcademicYearRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateClassRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateSectionRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateSubjectRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.PlaceStudentRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.SectionResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.StudentPlacementResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.SubjectResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.SubjectTeacherResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateAcademicYearRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateClassRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateSectionRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateSubjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Staff-only, SCHOOL tenants only (SchoolTenantGuard, inside each service).
 * Parents and students never reach these endpoints. Permissions reuse the
 * existing batch/student ones: classes and sections are the school's
 * equivalent of batches.
 */
@RestController
@RequestMapping("/api/v1/academic")
public class AcademicController {

    private final AcademicYearService yearService;
    private final ClassSectionService classSectionService;
    private final SubjectService subjectService;
    private final StudentPlacementService placementService;

    public AcademicController(AcademicYearService yearService, ClassSectionService classSectionService,
            SubjectService subjectService, StudentPlacementService placementService) {
        this.yearService = yearService;
        this.classSectionService = classSectionService;
        this.subjectService = subjectService;
        this.placementService = placementService;
    }

    // ---- Academic years ----

    @GetMapping("/years")
    @PreAuthorize("@perm.has('BATCHES_VIEW')")
    public ResponseEntity<List<AcademicYearResponse>> listYears() {
        return ResponseEntity.ok(yearService.list());
    }

    @PostMapping("/years")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<AcademicYearResponse> createYear(@Valid @RequestBody CreateAcademicYearRequest request) {
        return ResponseEntity.ok(yearService.create(request));
    }

    @PatchMapping("/years/{id}")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<AcademicYearResponse> updateYear(@PathVariable UUID id,
            @Valid @RequestBody UpdateAcademicYearRequest request) {
        return ResponseEntity.ok(yearService.update(id, request));
    }

    @PostMapping("/years/{id}/set-current")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<AcademicYearResponse> setCurrentYear(@PathVariable UUID id) {
        return ResponseEntity.ok(yearService.setCurrent(id));
    }

    // ---- Classes ----

    @GetMapping("/classes")
    @PreAuthorize("@perm.has('BATCHES_VIEW')")
    public ResponseEntity<List<ClassResponse>> listClasses() {
        return ResponseEntity.ok(classSectionService.listClasses());
    }

    @PostMapping("/classes")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<ClassResponse> createClass(@Valid @RequestBody CreateClassRequest request) {
        return ResponseEntity.ok(classSectionService.createClass(request));
    }

    @PatchMapping("/classes/{id}")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<ClassResponse> updateClass(@PathVariable UUID id,
            @Valid @RequestBody UpdateClassRequest request) {
        return ResponseEntity.ok(classSectionService.updateClass(id, request));
    }

    // ---- Sections ----

    @GetMapping("/sections")
    @PreAuthorize("@perm.has('BATCHES_VIEW')")
    public ResponseEntity<List<SectionResponse>> listSections(@RequestParam(required = false) UUID classId) {
        return ResponseEntity.ok(classSectionService.listSections(classId));
    }

    @PostMapping("/sections")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<SectionResponse> createSection(@Valid @RequestBody CreateSectionRequest request) {
        return ResponseEntity.ok(classSectionService.createSection(request));
    }

    @PatchMapping("/sections/{id}")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<SectionResponse> updateSection(@PathVariable UUID id,
            @Valid @RequestBody UpdateSectionRequest request) {
        return ResponseEntity.ok(classSectionService.updateSection(id, request));
    }

    @GetMapping("/sections/{id}/students")
    @PreAuthorize("@perm.has('STUDENTS_VIEW')")
    public ResponseEntity<List<StudentPlacementResponse>> sectionRoster(@PathVariable UUID id) {
        return ResponseEntity.ok(placementService.roster(id));
    }

    // ---- Subjects ----

    @GetMapping("/subjects")
    @PreAuthorize("@perm.has('BATCHES_VIEW')")
    public ResponseEntity<List<SubjectResponse>> listSubjects() {
        return ResponseEntity.ok(subjectService.list());
    }

    @PostMapping("/subjects")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody CreateSubjectRequest request) {
        return ResponseEntity.ok(subjectService.create(request));
    }

    @PatchMapping("/subjects/{id}")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<SubjectResponse> updateSubject(@PathVariable UUID id,
            @Valid @RequestBody UpdateSubjectRequest request) {
        return ResponseEntity.ok(subjectService.update(id, request));
    }

    // ---- Subject teachers per section ----

    @GetMapping("/sections/{id}/subject-teachers")
    @PreAuthorize("@perm.has('BATCHES_VIEW')")
    public ResponseEntity<List<SubjectTeacherResponse>> listSubjectTeachers(@PathVariable UUID id) {
        return ResponseEntity.ok(subjectService.listSubjectTeachers(id));
    }

    @PutMapping("/sections/{id}/subject-teachers/{subjectId}")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<SubjectTeacherResponse> assignTeacher(@PathVariable UUID id, @PathVariable UUID subjectId,
            @Valid @RequestBody AssignTeacherRequest request) {
        return ResponseEntity.ok(subjectService.assignTeacher(id, subjectId, request.teacherUserId()));
    }

    @DeleteMapping("/sections/{id}/subject-teachers/{subjectId}")
    @PreAuthorize("@perm.has('BATCHES_EDIT')")
    public ResponseEntity<Void> removeTeacher(@PathVariable UUID id, @PathVariable UUID subjectId) {
        subjectService.removeTeacher(id, subjectId);
        return ResponseEntity.noContent().build();
    }

    // ---- Student placement ----

    @PutMapping("/students/{studentId}/placement")
    @PreAuthorize("@perm.has('STUDENTS_EDIT')")
    public ResponseEntity<StudentPlacementResponse> place(@PathVariable UUID studentId,
            @Valid @RequestBody PlaceStudentRequest request) {
        return ResponseEntity.ok(placementService.place(studentId, request));
    }

    @DeleteMapping("/students/{studentId}/placement")
    @PreAuthorize("@perm.has('STUDENTS_EDIT')")
    public ResponseEntity<Void> unplace(@PathVariable UUID studentId) {
        placementService.unplace(studentId);
        return ResponseEntity.noContent().build();
    }
}
