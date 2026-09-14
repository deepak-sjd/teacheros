package com.teacheros.modules.fee.dto;

import com.teacheros.modules.fee.FeeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class FeeDtos {

    public record CreateFeeRequest(
            @NotNull UUID studentId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amountDue,
            @NotNull LocalDate dueDate
    ) {
    }

    public record RecordPaymentRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amountPaid,
            String method
    ) {
    }

    public record FeeResponse(
            UUID id,
            UUID studentId,
            BigDecimal amountDue,
            BigDecimal amountPaid,
            LocalDate dueDate,
            FeeStatus status
    ) {
    }
}
