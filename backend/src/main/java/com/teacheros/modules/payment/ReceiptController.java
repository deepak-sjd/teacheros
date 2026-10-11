package com.teacheros.modules.payment;

import com.teacheros.modules.payment.dto.ReceiptDtos.PaymentReceiptResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * No @PreAuthorize - open to any authenticated role, ReceiptService
 * enforces "your own child / yourself only" via StudentAccessGuard.
 */
@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping("/fees/{feeId}")
    public ResponseEntity<List<PaymentReceiptResponse>> listForFee(@PathVariable UUID feeId) {
        return ResponseEntity.ok(receiptService.listForFee(feeId));
    }

    @GetMapping(value = "/payments/{paymentId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receiptPdf(@PathVariable UUID paymentId) {
        byte[] pdf = receiptService.generatePdf(paymentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("receipt-" + paymentId + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
