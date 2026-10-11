package com.teacheros.modules.lead;

import com.teacheros.modules.lead.dto.LeadDtos.EnquiryRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * No @PreAuthorize - this whole /api/v1/public/** tree is permitAll in
 * SecurityConfig. Write-only by design: 202 with no body, no GET.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicEnquiryController {

    private final PublicEnquiryService publicEnquiryService;

    public PublicEnquiryController(PublicEnquiryService publicEnquiryService) {
        this.publicEnquiryService = publicEnquiryService;
    }

    @PostMapping("/enquiries")
    public ResponseEntity<Void> submit(@Valid @RequestBody EnquiryRequest request, HttpServletRequest http) {
        publicEnquiryService.submit(request, http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }
}
