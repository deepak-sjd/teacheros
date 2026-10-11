package com.teacheros.modules.feestructure.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class FeeStructureDtos {

    // ---- heads ----

    public record CreateFeeHeadRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) String code,
            Boolean siblingConcessionEligible,
            Integer sortOrder) {
    }

    public record UpdateFeeHeadRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 30) String code,
            @NotNull Boolean siblingConcessionEligible,
            @NotNull Boolean active,
            @NotNull Integer sortOrder) {
    }

    public record FeeHeadResponse(
            UUID id,
            String name,
            String code,
            boolean siblingConcessionEligible,
            boolean active,
            int sortOrder) {
    }

    // ---- instalment plans ----

    public record InstalmentInput(
            @NotNull LocalDate dueDate,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentage) {
    }

    public record CreateInstalmentPlanRequest(
            @NotBlank @Size(max = 100) String name,
            @NotEmpty @Valid List<InstalmentInput> instalments) {
    }

    public record InstalmentResponse(
            int instalmentNo,
            LocalDate dueDate,
            BigDecimal percentage) {
    }

    public record InstalmentPlanResponse(
            UUID id,
            String name,
            int numberOfInstalments,
            boolean active,
            List<InstalmentResponse> instalments) {
    }

    // ---- structures ----

    public record StructureItemInput(
            @NotNull UUID headId,
            @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record CreateFeeStructureRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Use the form 2026-27") String academicYear,
            @Size(max = 50) String grade,
            UUID batchId,
            @NotNull UUID planId,
            @NotEmpty @Valid List<StructureItemInput> items) {
    }

    // Items are fixed once created: fees already generated from them must
    // keep matching the structure. Create a new structure for a new price.
    public record UpdateFeeStructureRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull Boolean active) {
    }

    public record StructureItemResponse(
            UUID headId,
            String headName,
            BigDecimal amount) {
    }

    public record FeeStructureResponse(
            UUID id,
            String name,
            String academicYear,
            String grade,
            UUID batchId,
            UUID planId,
            String planName,
            boolean active,
            List<StructureItemResponse> items,
            BigDecimal totalAmount) {
    }

    // ---- assignment ----

    /** studentIds null/empty = every active student in the structure's batch/grade scope. */
    public record AssignStructureRequest(
            @NotNull UUID structureId,
            List<UUID> studentIds) {
    }

    public record AssignmentPreviewRow(
            UUID studentId,
            String studentName,
            String grade,
            int instalmentNo,
            LocalDate dueDate,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal amountDue,
            String discountNotes,
            boolean alreadyAssigned) {
    }

    public record AssignmentPreviewResponse(
            UUID structureId,
            String structureName,
            int studentCount,
            int feesToCreate,
            int feesAlreadyAssigned,
            BigDecimal totalToCharge,
            BigDecimal totalDiscount,
            List<AssignmentPreviewRow> rows) {
    }

    public record AssignmentResultResponse(
            int studentCount,
            int created,
            int skipped,
            BigDecimal totalCharged) {
    }
}
