package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.feestructure.FeePaymentReadRepository.PaidTotalRow;
import com.teacheros.modules.feestructure.FeePaymentReadRepository.PaymentReadRow;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DailyCollectionResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DefaulterRow;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DefaultersResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.HeadCollectionResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.HeadCollectionRow;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.MethodTotal;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.OutstandingResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.OutstandingRow;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.PaymentEntry;
import com.teacheros.modules.student.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accountant reports. Read-only, computed on demand (nothing stored), and
 * built from a fixed number of set-based queries - never one query per fee -
 * so a 5,000-fee tenant stays well inside the 3 second target.
 *
 * All day boundaries are Asia/Kolkata: "the IST day" starts at 00:00 IST and
 * ends before 00:00 IST the next day, converted to Instants for the query.
 * All sums are BigDecimal.
 */
@Service
public class FeeReportService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int IN_CHUNK = 1000;

    private final FeeAssignmentRepository feeRepository;
    private final FeePaymentReadRepository paymentRepository;
    private final StudentLookupRepository studentLookup;
    private final BatchRepository batchRepository;
    private final FeeLineItemRepository lineItemRepository;
    private final FeeHeadRepository headRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeReportService(FeeAssignmentRepository feeRepository, FeePaymentReadRepository paymentRepository,
            StudentLookupRepository studentLookup, BatchRepository batchRepository,
            FeeLineItemRepository lineItemRepository, FeeHeadRepository headRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.feeRepository = feeRepository;
        this.paymentRepository = paymentRepository;
        this.studentLookup = studentLookup;
        this.batchRepository = batchRepository;
        this.lineItemRepository = lineItemRepository;
        this.headRepository = headRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    // ---------------------------------------------------------------- daily

    public DailyCollectionResponse dailyCollection(LocalDate date) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        LocalDate day = date != null ? date : LocalDate.now(IST);
        Instant from = day.atStartOfDay(IST).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(IST).toInstant();

        List<PaymentReadRow> rows = paymentRepository.findInRange(tenantId, from, to);
        Map<UUID, Fee> fees = loadFees(tenantId, rows.stream().map(PaymentReadRow::getFeeId).collect(Collectors.toSet()));
        Map<UUID, Student> students = loadStudents(tenantId,
                fees.values().stream().map(Fee::getStudentId).collect(Collectors.toSet()));

        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byMethod = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        List<PaymentEntry> entries = new ArrayList<>();
        for (PaymentReadRow p : rows) {
            BigDecimal amount = FeeCalculator.scale(p.getAmountPaid());
            String method = normalizeMethod(p.getMethod());
            total = total.add(amount);
            byMethod.merge(method, amount, BigDecimal::add);
            counts.merge(method, 1L, Long::sum);
            Fee fee = fees.get(p.getFeeId());
            Student s = fee == null ? null : students.get(fee.getStudentId());
            entries.add(new PaymentEntry(p.getPaidAt(), s == null ? "-" : s.getFullName(), method, amount));
        }
        List<MethodTotal> methods = byMethod.entrySet().stream()
                .map(e -> new MethodTotal(e.getKey(), counts.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(MethodTotal::amount).reversed())
                .toList();
        return new DailyCollectionResponse(day, total, rows.size(), methods, entries);
    }

    // ---------------------------------------------------------- outstanding

    private record OutstandingFee(Fee fee, BigDecimal paid, BigDecimal outstanding) {
    }

    private List<OutstandingFee> loadOutstanding(UUID tenantId) {
        List<Fee> fees = feeRepository.findByTenantIdAndStatusIn(tenantId,
                List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID, FeeStatus.OVERDUE));
        Map<UUID, BigDecimal> paid = paymentRepository.sumPaidByFee(tenantId).stream()
                .collect(Collectors.toMap(PaidTotalRow::getFeeId, PaidTotalRow::getTotal));
        List<OutstandingFee> out = new ArrayList<>();
        for (Fee f : fees) {
            BigDecimal p = paid.getOrDefault(f.getId(), BigDecimal.ZERO);
            BigDecimal due = f.getAmountDue().subtract(p).max(BigDecimal.ZERO);
            if (due.signum() > 0) {
                out.add(new OutstandingFee(f, p, due));
            }
        }
        return out;
    }

    private static class Acc {
        UUID batchId;
        Set<UUID> students = new HashSet<>();
        long fees;
        BigDecimal due = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
    }

    public OutstandingResponse outstanding(String groupBy) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        boolean byGrade = "grade".equalsIgnoreCase(groupBy);
        if (groupBy != null && !byGrade && !"batch".equalsIgnoreCase(groupBy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GROUP_BY", "groupBy must be batch or grade.");
        }

        List<OutstandingFee> open = loadOutstanding(tenantId);
        Map<UUID, Student> students = loadStudents(tenantId,
                open.stream().map(o -> o.fee().getStudentId()).collect(Collectors.toSet()));
        Map<UUID, String> batchNames = batchRepository.findAllByTenantIdAndActiveTrue(tenantId).stream()
                .collect(Collectors.toMap(Batch::getId, Batch::getName));

        Map<String, Acc> groups = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (OutstandingFee o : open) {
            Student s = students.get(o.fee().getStudentId());
            if (s == null) {
                continue;
            }
            String key;
            UUID batchId = null;
            if (byGrade) {
                key = s.getGrade() == null || s.getGrade().isBlank() ? "No grade" : s.getGrade().trim();
            } else {
                batchId = s.getBatchId();
                key = batchId == null ? "No batch" : batchNames.getOrDefault(batchId, "Other batch");
            }
            Acc a = groups.computeIfAbsent(key, k -> new Acc());
            a.batchId = batchId;
            a.students.add(s.getId());
            a.fees++;
            a.due = a.due.add(o.fee().getAmountDue());
            a.paid = a.paid.add(o.paid());
            a.outstanding = a.outstanding.add(o.outstanding());
            total = total.add(o.outstanding());
        }
        List<OutstandingRow> rows = groups.entrySet().stream()
                .map(e -> new OutstandingRow(e.getKey(), e.getValue().batchId, e.getValue().students.size(),
                        e.getValue().fees, e.getValue().due, e.getValue().paid, e.getValue().outstanding))
                .sorted(Comparator.comparing(OutstandingRow::outstanding).reversed())
                .toList();
        return new OutstandingResponse(byGrade ? "grade" : "batch", LocalDate.now(IST), total, rows);
    }

    // ------------------------------------------------------------ defaulters

    public DefaultersResponse defaulters(int minDaysOverdue) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        LocalDate today = LocalDate.now(IST);

        List<OutstandingFee> overdue = loadOutstanding(tenantId).stream()
                .filter(o -> ChronoUnit.DAYS.between(o.fee().getDueDate(), today) > Math.max(minDaysOverdue, 0) - 1
                        && o.fee().getDueDate().isBefore(today))
                .toList();
        Map<UUID, Student> students = loadStudents(tenantId,
                overdue.stream().map(o -> o.fee().getStudentId()).collect(Collectors.toSet()));
        Map<UUID, String> batchNames = batchRepository.findAllByTenantIdAndActiveTrue(tenantId).stream()
                .collect(Collectors.toMap(Batch::getId, Batch::getName));

        Map<UUID, List<OutstandingFee>> byStudent = overdue.stream()
                .collect(Collectors.groupingBy(o -> o.fee().getStudentId()));
        List<DefaulterRow> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<OutstandingFee>> e : byStudent.entrySet()) {
            Student s = students.get(e.getKey());
            if (s == null) {
                continue;
            }
            BigDecimal amount = e.getValue().stream().map(OutstandingFee::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate oldest = e.getValue().stream().map(o -> o.fee().getDueDate()).min(Comparator.naturalOrder()).orElse(today);
            rows.add(new DefaulterRow(s.getId(), s.getFullName(), s.getGrade(),
                    s.getBatchId() == null ? "No batch" : batchNames.getOrDefault(s.getBatchId(), "Other batch"),
                    s.getGuardianPhone(), e.getValue().size(), amount, oldest, ChronoUnit.DAYS.between(oldest, today)));
            total = total.add(amount);
        }
        rows.sort(Comparator.comparing(DefaulterRow::overdueAmount).reversed());
        return new DefaultersResponse(today, total, rows);
    }

    // ---------------------------------------------------------- per fee head

    public HeadCollectionResponse collectionByHead(LocalDate from, LocalDate to) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        LocalDate end = to != null ? to : LocalDate.now(IST);
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "\"from\" must not be after \"to\".");
        }

        List<PaymentReadRow> payments = paymentRepository.findInRange(tenantId,
                start.atStartOfDay(IST).toInstant(), end.plusDays(1).atStartOfDay(IST).toInstant());
        Set<UUID> feeIds = payments.stream().map(PaymentReadRow::getFeeId).collect(Collectors.toSet());
        Map<UUID, Fee> fees = loadFees(tenantId, feeIds);
        Map<UUID, List<FeeLineItem>> lines = new LinkedHashMap<>();
        for (List<UUID> chunk : chunks(feeIds)) {
            lineItemRepository.findByTenantIdAndFeeIdIn(tenantId, chunk)
                    .forEach(l -> lines.computeIfAbsent(l.getFeeId(), k -> new ArrayList<>()).add(l));
        }
        Map<UUID, String> headNames = headRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FeeHead::getId, FeeHead::getName));

        // A payment belongs to a fee, not a head, so it is split across the
        // fee's lines pro rata to each line's net amount (remainder on the
        // last line - the parts always sum to the payment).
        Map<String, BigDecimal> byHead = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentReadRow p : payments) {
            BigDecimal amount = FeeCalculator.scale(p.getAmountPaid());
            total = total.add(amount);
            List<FeeLineItem> feeLines = lines.getOrDefault(p.getFeeId(), List.of());
            List<BigDecimal> weights = feeLines.stream().map(FeeLineItem::netAmount).toList();
            boolean splittable = !feeLines.isEmpty() && weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0;
            if (!splittable) {
                Fee fee = fees.get(p.getFeeId());
                String label = fee != null && fee.getLineType() == FeeLineType.LATE_FEE ? "Late fee" : "Unclassified";
                byHead.merge(label, amount, BigDecimal::add);
                continue;
            }
            List<BigDecimal> parts = FeeCalculator.allocate(amount, weights);
            for (int i = 0; i < feeLines.size(); i++) {
                byHead.merge(headNames.getOrDefault(feeLines.get(i).getHeadId(), "Unknown"), parts.get(i), BigDecimal::add);
            }
        }
        List<HeadCollectionRow> rows = byHead.entrySet().stream()
                .map(e -> new HeadCollectionRow(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(HeadCollectionRow::amount).reversed())
                .toList();
        return new HeadCollectionResponse(start, end, total, rows);
    }

    // --------------------------------------------------------------- helpers

    /** Existing app uses CASH / UPI / BANK_TRANSFER but also "Cash", "Bank transfer", "razorpay". */
    static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "UNSPECIFIED";
        }
        return method.trim().toUpperCase().replace(' ', '_');
    }

    private Map<UUID, Fee> loadFees(UUID tenantId, Set<UUID> ids) {
        Map<UUID, Fee> out = new LinkedHashMap<>();
        for (List<UUID> chunk : chunks(ids)) {
            feeRepository.findByTenantIdAndIdIn(tenantId, chunk).forEach(f -> out.put(f.getId(), f));
        }
        return out;
    }

    private Map<UUID, Student> loadStudents(UUID tenantId, Set<UUID> ids) {
        Map<UUID, Student> out = new LinkedHashMap<>();
        for (List<UUID> chunk : chunks(ids)) {
            studentLookup.findByTenantIdAndIdIn(tenantId, chunk).forEach(s -> out.put(s.getId(), s));
        }
        return out;
    }

    private static List<List<UUID>> chunks(Collection<UUID> ids) {
        List<UUID> all = new ArrayList<>(ids);
        List<List<UUID>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += IN_CHUNK) {
            out.add(all.subList(i, Math.min(i + IN_CHUNK, all.size())));
        }
        return out;
    }
}
