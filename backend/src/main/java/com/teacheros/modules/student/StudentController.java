package com.teacheros.modules.student;

import com.teacheros.modules.student.dto.StudentDtos.CreateStudentRequest;
import com.teacheros.modules.student.dto.StudentDtos.StudentResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Page<StudentResponse>> list(
            @RequestParam(required = false) UUID batchId,
            Pageable pageable) {
        return ResponseEntity.ok(studentService.list(batchId, pageable));
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
