package com.teacheros.modules.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ReceiptDtos {

    public record PaymentReceiptResponse(
            UUID id,
            String receiptNumber,
            BigDecimal amountPaid,
            Instant paidAt,
            String method) {
    }
}
