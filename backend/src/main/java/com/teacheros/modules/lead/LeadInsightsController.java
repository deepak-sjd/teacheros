package com.teacheros.modules.lead;

import com.teacheros.modules.demo.dto.FunnelDtos.FunnelResponse;
import com.teacheros.modules.lead.dto.LeadDtos.LeadResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Shares /api/v1/leads with LeadController; literal paths here win over its
 * /{id}.
 */
@RestController
@RequestMapping("/api/v1/leads")
public class LeadInsightsController {

    private final LeadService leadService;
    private final LeadFunnelService funnelService;

    public LeadInsightsController(LeadService leadService, LeadFunnelService funnelService) {
        this.leadService = leadService;
        this.funnelService = funnelService;
    }

    // Counsellor page: leads whose follow-up is due or overdue. mine=false shows
    // everyone's.
    @GetMapping("/follow-ups")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STAFF','COUNSELLOR')")
    public ResponseEntity<List<LeadResponse>> followUps(@RequestParam(defaultValue = "true") boolean mine) {
        return ResponseEntity.ok(leadService.dueFollowUps(mine));
    }

    @GetMapping("/funnel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','COUNSELLOR')")
    public ResponseEntity<FunnelResponse> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(funnelService.funnel(from, to));
    }

    @GetMapping(value = "/funnel/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','COUNSELLOR')")
    public ResponseEntity<String> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("lead-funnel.csv").build());
        return ResponseEntity.ok().headers(headers).body(funnelService.exportCsv(from, to));
    }
}
