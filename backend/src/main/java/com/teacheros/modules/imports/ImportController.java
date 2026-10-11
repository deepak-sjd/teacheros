package com.teacheros.modules.imports;

import com.teacheros.modules.imports.dto.ImportDtos.ImportResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Staff-only, same as BatchController - parents/students never import data.
@RestController
@RequestMapping("/api/v1/imports")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class ImportController {

    private final StudentImportService studentImportService;

    public ImportController(StudentImportService studentImportService) {
        this.studentImportService = studentImportService;
    }

    @PostMapping(value = "/students", consumes = "multipart/form-data")
    public ResponseEntity<ImportResult> importStudents(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean skipInvalidRows) {
        return ResponseEntity.ok(studentImportService.importStudents(file, dryRun, skipInvalidRows));
    }
}
