package com.teacheros.modules.demo;

import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoBookingRequest;
import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoBookingResponse;
import com.teacheros.modules.demo.dto.DemoDtos.PublicDemoSlot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * No @PreAuthorize: /api/v1/public/** is permitAll. Seat counts change, so
 * nothing here is cached.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicDemoController {

    private final PublicDemoService publicDemoService;

    public PublicDemoController(PublicDemoService publicDemoService) {
        this.publicDemoService = publicDemoService;
    }

    @GetMapping("/schools/{slug}/demo-slots")
    public ResponseEntity<List<PublicDemoSlot>> slots(@PathVariable String slug,
            @RequestParam(required = false) UUID courseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicDemoService.slots(slug, courseId));
    }

    @PostMapping("/demo-bookings")
    public ResponseEntity<PublicDemoBookingResponse> book(@Valid @RequestBody PublicDemoBookingRequest request,
            HttpServletRequest http) {
        PublicDemoBookingResponse body = publicDemoService.book(request, http.getRemoteAddr());
        return body == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(body);
    }
}
