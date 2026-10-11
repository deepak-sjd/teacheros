package com.teacheros.modules.student;

import com.teacheros.modules.student.dto.StudentDtos.CreateStudentRequest;
import com.teacheros.modules.student.dto.StudentDtos.StudentResponse;
import com.teacheros.modules.student.dto.StudentDtos.UpdateStudentRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.ok(studentService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<StudentResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateStudentRequest request) {
        return ResponseEntity.ok(studentService.update(id, request));
    }

    // Soft delete - history is kept, see StudentService#deactivate.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        studentService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // Default sort (name, then id as a tiebreaker) keeps page boundaries
    // stable between requests. When a search term is given, the query in
    // StudentRepository#search fixes its own ordering, so any sort on the
    // Pageable is dropped (page number and size are kept).
    @GetMapping
    @PreAuthorize("@perm.has('STUDENTS_VIEW')")
    public ResponseEntity<Page<StudentResponse>> list(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = { "fullName", "id" }) Pageable pageable) {
        Pageable effective = (search != null && !search.isBlank())
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
                : pageable;
        return ResponseEntity.ok(studentService.list(batchId, search, effective));
    }

    // Resolves a set of IDs to student records (names for a page of fees,
    // etc.). Literal path, so it never collides with /{id}.
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<StudentResponse>> lookup(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(studentService.lookup(ids));
    }

    // No @PreAuthorize restricting this one to staff roles - PARENT and
    // STUDENT need to reach it too (for their own linked student, via the
    // web/mobile student detail screens). StudentService.get() enforces the
    // fine-grained "your own child / yourself only" check via
    // StudentAccessGuard - see that class for why the check lives there
    // and not here.
    @GetMapping("/{id}")
    public ResponseEntity<StudentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(studentService.get(id));
    }

    @GetMapping("/me/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<StudentResponse>> myChildren() {
        return ResponseEntity.ok(studentService.listMyChildren());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentResponse> myStudentRecord() {
        return ResponseEntity.ok(studentService.getMyStudentRecord());
    }
}
