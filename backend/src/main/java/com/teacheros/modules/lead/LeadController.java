package com.teacheros.modules.lead;

import com.teacheros.modules.lead.dto.LeadDtos.AssigneeResponse;
import com.teacheros.modules.lead.dto.LeadDtos.AssignLeadRequest;
import com.teacheros.modules.lead.dto.LeadDtos.ConvertLeadRequest;
import com.teacheros.modules.lead.dto.LeadDtos.ConvertLeadResponse;
import com.teacheros.modules.lead.dto.LeadDtos.FollowUpRequest;
import com.teacheros.modules.lead.dto.LeadDtos.LeadCountsResponse;
import com.teacheros.modules.lead.dto.LeadDtos.LeadDetailResponse;
import com.teacheros.modules.lead.dto.LeadDtos.LeadResponse;
import com.teacheros.modules.lead.dto.LeadDtos.UpdateLeadStatusRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leads")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    public ResponseEntity<Page<LeadResponse>> list(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) UUID assignedTo,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leadService.list(status, assignedTo, pageable));
    }

    @GetMapping("/counts")
    public ResponseEntity<LeadCountsResponse> counts() {
        return ResponseEntity.ok(leadService.counts());
    }

    @GetMapping("/assignees")
    public ResponseEntity<List<AssigneeResponse>> assignees() {
        return ResponseEntity.ok(leadService.listAssignees());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(leadService.get(id));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<LeadResponse> assign(@PathVariable UUID id, @Valid @RequestBody AssignLeadRequest request) {
        return ResponseEntity.ok(leadService.assign(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<LeadResponse> updateStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateLeadStatusRequest request) {
        return ResponseEntity.ok(leadService.updateStatus(id, request));
    }

    @PostMapping("/{id}/follow-up")
    public ResponseEntity<LeadResponse> followUp(@PathVariable UUID id, @Valid @RequestBody FollowUpRequest request) {
        return ResponseEntity.ok(leadService.addFollowUp(id, request));
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<ConvertLeadResponse> convert(@PathVariable UUID id,
            @Valid @RequestBody ConvertLeadRequest request) {
        return ResponseEntity.ok(leadService.convert(id, request));
    }
}
