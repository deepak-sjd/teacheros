package com.teacheros.modules.compliance;

import com.teacheros.modules.compliance.dto.ComplianceDtos.ConsentStatusResponse;
import com.teacheros.modules.compliance.dto.ComplianceDtos.GrantConsentRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consent")
@PreAuthorize("hasRole('PARENT')")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping("/status")
    public ResponseEntity<ConsentStatusResponse> status() {
        return ResponseEntity.ok(consentService.status());
    }

    @PostMapping("/grant")
    public ResponseEntity<ConsentStatusResponse> grant(@Valid @RequestBody GrantConsentRequest request) {
        return ResponseEntity.ok(consentService.grant(request));
    }

    @PostMapping("/students/{studentId}/withdraw")
    public ResponseEntity<ConsentStatusResponse> withdraw(@PathVariable UUID studentId) {
        return ResponseEntity.ok(consentService.withdraw(studentId));
    }
}
