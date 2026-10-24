package com.teacheros.modules.compliance;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// No @PreAuthorize - StudentDataExportService decides (staff or the student's own guardian).
@RestController
@RequestMapping("/api/v1/students")
public class StudentDataExportController {

    private final StudentDataExportService exportService;

    public StudentDataExportController(StudentDataExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{id}/data-export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        byte[] zip = exportService.export(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("student-data-" + id + ".zip").build());
        return ResponseEntity.ok().headers(headers).body(zip);
    }
}
