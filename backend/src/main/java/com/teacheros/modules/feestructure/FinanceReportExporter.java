package com.teacheros.modules.feestructure;

import com.teacheros.common.export.ExcelWorkbookBuilder;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DailyCollectionResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.DefaultersResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.HeadCollectionResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.OutstandingResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Builds the four Excel files from the same response objects the JSON endpoints return. */
@Component
public class FinanceReportExporter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");

    public byte[] dailyCollection(DailyCollectionResponse r) {
        try (ExcelWorkbookBuilder b = new ExcelWorkbookBuilder("Daily collection")) {
            b.title("Daily collection - " + r.date());
            b.headerRow("Method", "Payments", "Amount (Rs.)");
            r.byMethod().forEach(m -> b.row(m.method(), m.count(), m.amount()));
            b.totalRow("Total", r.paymentCount(), r.totalCollected());
            b.blankRow();
            b.headerRow("Student", "Time (IST)", "Method", "Amount (Rs.)");
            r.payments().forEach(p -> b.row(p.studentName(),
                    TIME.format(p.paidAt().atZone(FeeReportService.IST)), p.method(), p.amount()));
            return b.toBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Excel export", e);
        }
    }

    public byte[] outstanding(OutstandingResponse r) {
        try (ExcelWorkbookBuilder b = new ExcelWorkbookBuilder("Outstanding")) {
            b.title("Outstanding fees by " + r.groupBy() + " - as of " + r.asOf());
            b.headerRow(r.groupBy().equals("grade") ? "Grade" : "Batch", "Students", "Fees", "Due (Rs.)", "Paid (Rs.)",
                    "Outstanding (Rs.)");
            BigDecimal due = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            long fees = 0;
            r.rows().forEach(row -> b.row(row.group(), row.studentCount(), row.feeCount(), row.totalDue(),
                    row.totalPaid(), row.outstanding()));
            for (var row : r.rows()) {
                due = due.add(row.totalDue());
                paid = paid.add(row.totalPaid());
                fees += row.feeCount();
            }
            b.totalRow("Total", null, fees, due, paid, r.totalOutstanding());
            return b.toBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Excel export", e);
        }
    }

    public byte[] defaulters(DefaultersResponse r) {
        try (ExcelWorkbookBuilder b = new ExcelWorkbookBuilder("Defaulters")) {
            b.title("Defaulters - as of " + r.asOf());
            b.headerRow("Student", "Grade", "Batch", "Guardian phone", "Overdue fees", "Overdue (Rs.)",
                    "Oldest due date", "Days overdue");
            r.rows().forEach(d -> b.row(d.studentName(), d.grade(), d.batchName(), d.guardianPhone(),
                    d.overdueFeeCount(), d.overdueAmount(), d.oldestDueDate(), d.maxDaysOverdue()));
            b.totalRow("Total", null, null, null, null, r.totalOverdue());
            return b.toBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Excel export", e);
        }
    }

    public byte[] collectionByHead(HeadCollectionResponse r) {
        try (ExcelWorkbookBuilder b = new ExcelWorkbookBuilder("Collection by head")) {
            b.title("Collection per fee head - " + r.from() + " to " + r.to());
            b.headerRow("Fee head", "Collected (Rs.)");
            r.rows().forEach(h -> b.row(h.head(), h.amount()));
            b.totalRow("Total", r.totalCollected());
            return b.toBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Excel export", e);
        }
    }
}
