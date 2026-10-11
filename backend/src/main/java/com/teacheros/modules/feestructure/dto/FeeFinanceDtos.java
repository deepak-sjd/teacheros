package com.teacheros.modules.feestructure.dto;

import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.feestructure.AmountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Discounts, rules, ledger breakdown and accountant report DTOs. */
public class FeeFinanceDtos {

    // ---- discounts ----

    public record CreateFeeDiscountRequest(
            @NotNull UUID studentId,
            UUID feeHeadId,
            @NotNull AmountType discountType,
            @NotNull @DecimalMin("0.01") BigDecimal value,
            @NotBlank @Size(max = 255) String reason) {
    }

    public record UpdateFeeDiscountRequest(
            @NotNull AmountType discountType,
            @NotNull @DecimalMin("0.01") BigDecimal value,
            @NotBlank @Size(max = 255) String reason,
            @NotNull Boolean active) {
    }

    public record FeeDiscountResponse(
            UUID id,
            UUID studentId,
            String studentName,
            UUID feeHeadId,
            String feeHeadName,
            AmountType discountType,
            BigDecimal value,
            String reason,
            UUID approvedByUserId,
            boolean active,
            Instant createdAt) {
    }

    public record WaiveLateFeeRequest(
            @NotNull UUID feeId,
            @NotBlank @Size(max = 255) String reason) {
    }

    // ---- tenant rules ----

    public record FeeRulesRequest(
            @NotNull AmountType siblingConcessionType,
            @NotNull @DecimalMin("0.00") BigDecimal siblingConcessionValue,
            @NotNull AmountType lateFeeType,
            @NotNull @DecimalMin("0.00") BigDecimal lateFeeValue,
            @NotNull @Min(0) @Max(365) Integer lateFeeGraceDays) {
    }

    public record FeeRulesResponse(
            AmountType siblingConcessionType,
            BigDecimal siblingConcessionValue,
            AmountType lateFeeType,
            BigDecimal lateFeeValue,
            int lateFeeGraceDays,
            Instant updatedAt) {
    }

    public record LateFeeRunResponse(int created) {
    }

    // ---- ledger breakdown ----

    public record LedgerHeadLine(
            String headName,
            BigDecimal grossAmount,
            BigDecimal discountAmount) {
    }

    /** One fee row, with the discount broken out so the UI can show it as its own line. */
    public record FeeLedgerLine(
            UUID feeId,
            FeeLineType lineType,
            Integer instalmentNo,
            String description,
            LocalDate dueDate,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal amountDue,
            BigDecimal amountPaid,
            FeeStatus status,
            List<LedgerHeadLine> heads) {
    }

    // ---- accountant reports ----

    public record MethodTotal(String method, long count, BigDecimal amount) {
    }

    public record PaymentEntry(Instant paidAt, String studentName, String method, BigDecimal amount) {
    }

    public record DailyCollectionResponse(
            LocalDate date,
            BigDecimal totalCollected,
            long paymentCount,
            List<MethodTotal> byMethod,
            List<PaymentEntry> payments) {
    }

    public record OutstandingRow(
            String group,
            UUID batchId,
            long studentCount,
            long feeCount,
            BigDecimal totalDue,
            BigDecimal totalPaid,
            BigDecimal outstanding) {
    }

    public record OutstandingResponse(
            String groupBy,
            LocalDate asOf,
            BigDecimal totalOutstanding,
            List<OutstandingRow> rows) {
    }

    public record DefaulterRow(
            UUID studentId,
            String studentName,
            String grade,
            String batchName,
            String guardianPhone,
            long overdueFeeCount,
            BigDecimal overdueAmount,
            LocalDate oldestDueDate,
            long maxDaysOverdue) {
    }

    public record DefaultersResponse(
            LocalDate asOf,
            BigDecimal totalOverdue,
            List<DefaulterRow> rows) {
    }

    public record HeadCollectionRow(String head, BigDecimal amount) {
    }

    public record HeadCollectionResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal totalCollected,
            List<HeadCollectionRow> rows) {
    }
}
