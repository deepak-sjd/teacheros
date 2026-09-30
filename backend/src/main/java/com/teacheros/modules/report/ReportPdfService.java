package com.teacheros.modules.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.AssessmentService;
import com.teacheros.modules.assessment.dto.MarkDtos.StudentMarkHistoryEntry;
import com.teacheros.modules.assessment.dto.RankingDtos.StudentRankResponse;
import com.teacheros.modules.attendance.AttendancePercentageCalculator;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.fee.FeeService;
import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.report.dto.ReportDtos.BatchAttendanceRow;
import com.teacheros.modules.report.dto.ReportDtos.BatchMarksRow;
import com.teacheros.modules.report.dto.ReportDtos.ReportSummaryResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Renders the same figures ReportService/AssessmentService already compute
 * into a downloadable PDF - a plain byte array, not stored anywhere (same
 * "computed on demand, nothing cached" philosophy as ReportService itself).
 * Two documents:
 *
 * - The staff-facing summary report (attendance/fees/marks for a tenant or
 * one batch, over a period) - the "Generate reports" feature.
 * - The parent/student-facing report card (one student's attendance, marks,
 * rank, and fee ledger, all-time) - the "download report card (PDF)"
 * feature. Guarded exactly like every other single-student endpoint: see
 * StudentAccessGuard.
 */
@Service
public class ReportPdfService {

    private static final DateTimeFormatter GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.ITALIC);
    private static final Font META_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, new java.awt.Color(90, 90, 90));
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font TABLE_CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font STAT_LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9,
            new java.awt.Color(90, 90, 90));
    private static final Font STAT_VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final java.awt.Color HEADER_ROW_COLOR = new java.awt.Color(235, 235, 235);

    private final ReportService reportService;
    private final AssessmentService assessmentService;
    private final FeeService feeService;
    private final TenantRepository tenantRepository;
    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public ReportPdfService(
            ReportService reportService,
            AssessmentService assessmentService,
            FeeService feeService,
            TenantRepository tenantRepository,
            BatchRepository batchRepository,
            StudentRepository studentRepository,
            AttendanceRepository attendanceRepository,
            StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.reportService = reportService;
        this.assessmentService = assessmentService;
        this.feeService = feeService;
        this.tenantRepository = tenantRepository;
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    // ---- Staff-facing summary report -------------------------------------

    public byte[] generateSummaryPdf(LocalDate from, LocalDate to, UUID batchId) {
        ReportSummaryResponse report = reportService.generate(from, to, batchId);
        Tenant tenant = currentTenant();
        String batchName = batchId != null ? batchName(batchId) : null;

        Document document = newDocument();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            addTitleBlock(document, tenant.getName(), "Performance Report",
                    formatDate(from) + " \u2013 " + formatDate(to)
                            + (batchName != null ? "  \u00b7  " + batchName : ""));

            addSectionHeading(document, "Attendance");
            addStatRow(document,
                    stat("Sessions held", String.valueOf(report.attendance().totalSessions())),
                    stat("Present records", String.valueOf(report.attendance().presentCount())),
                    stat("Attendance %", report.attendance().attendancePercentage() + "%"));
            if (!report.attendance().byBatch().isEmpty()) {
                PdfPTable table = newTable(4, new float[] { 3, 1.4f, 1.4f, 1.4f });
                addHeaderRow(table, "Batch", "Sessions", "Present", "Attendance %");
                for (BatchAttendanceRow row : report.attendance().byBatch()) {
                    addRow(table, row.batchName(), String.valueOf(row.sessions()), String.valueOf(row.presentCount()),
                            row.attendancePercentage() + "%");
                }
                document.add(table);
            }

            addSectionHeading(document, "Fees");
            document.add(smallNote(
                    "Fees aren't tied to a batch, so these figures are tenant-wide even when a batch is selected."));
            addStatRow(document,
                    stat("Collected this period", formatCurrency(report.fees().collectedInPeriod())),
                    stat("Outstanding", formatCurrency(report.fees().totalOutstanding())),
                    stat("Pending fees", String.valueOf(report.fees().pendingCount())),
                    stat("Overdue fees", String.valueOf(report.fees().overdueCount())));

            addSectionHeading(document, "Marks");
            addStatRow(document,
                    stat("Tests conducted", String.valueOf(report.marks().testsConducted())),
                    stat("Graded entries", String.valueOf(report.marks().gradedEntries())),
                    stat("Average score", report.marks().averageScorePercentage() + "%"));
            if (!report.marks().byBatch().isEmpty()) {
                PdfPTable table = newTable(4, new float[] { 3, 1.4f, 1.4f, 1.4f });
                addHeaderRow(table, "Batch", "Tests", "Graded", "Average %");
                for (BatchMarksRow row : report.marks().byBatch()) {
                    addRow(table, row.batchName(), String.valueOf(row.testsConducted()),
                            String.valueOf(row.gradedEntries()),
                            row.averageScorePercentage() + "%");
                }
                document.add(table);
            }

            addGeneratedFooter(document);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new UncheckedIOException("Failed to generate report PDF", new java.io.IOException(e));
        }
    }

    // ---- Parent/student-facing report card --------------------------------

    public byte[] generateStudentReportCardPdf(UUID studentId) {
        // Guarded once, up front, before touching anything else below -
        // same "your own child/self only" rule every other student-scoped
        // endpoint follows (see StudentAccessGuard's class javadoc).
        studentAccessGuard.assertCanView(studentId);

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        Tenant tenant = currentTenant();
        String batchName = student.getBatchId() != null ? batchName(student.getBatchId()) : "No batch assigned";

        long recorded = attendanceRepository.countRecordedSessions(tenantId, studentId);
        long attended = attendanceRepository.countAttendedSessions(tenantId, studentId);
        double attendancePct = AttendancePercentageCalculator.calculate(recorded, attended);

        StudentRankResponse rank = assessmentService.getStudentRank(studentId);
        var marks = assessmentService.getStudentMarkHistory(studentId);
        var fees = feeService.listForStudent(studentId);

        Document document = newDocument();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            addTitleBlock(document, tenant.getName(), "Student Report Card",
                    student.getFullName() + (student.getGrade() != null ? "  \u00b7  Grade " + student.getGrade() : "")
                            + "  \u00b7  " + batchName);

            addSectionHeading(document, "Overview");
            addStatRow(document,
                    stat("Attendance", attendancePct + "%", attended + " of " + recorded + " sessions"),
                    stat("Class rank",
                            rank.rank() != null ? "#" + rank.rank() + " of " + rank.rankedStudentCount()
                                    : "Not yet ranked"),
                    stat("Average score", rank.gradedTestCount() > 0 ? rank.averagePercentage() + "%" : "\u2014"));

            addSectionHeading(document, "Test results");
            if (marks.isEmpty()) {
                document.add(smallNote("Nothing graded yet."));
            } else {
                PdfPTable table = newTable(4, new float[] { 2.6f, 1.6f, 1.4f, 1.4f });
                addHeaderRow(table, "Test", "Subject", "Date", "Score");
                for (StudentMarkHistoryEntry m : marks) {
                    addRow(table, m.testTitle(), m.subject() != null ? m.subject() : "\u2014", formatDate(m.testDate()),
                            m.marksObtained() != null ? m.marksObtained() + " / " + m.maxMarks() : "Not graded");
                }
                document.add(table);
            }

            addSectionHeading(document, "Fee ledger");
            if (fees.isEmpty()) {
                document.add(smallNote("No fees on record."));
            } else {
                PdfPTable table = newTable(5, new float[] { 1.6f, 1.6f, 1.6f, 1.6f, 1.4f });
                addHeaderRow(table, "Due date", "Amount due", "Amount paid", "Balance", "Status");
                for (FeeResponse f : fees) {
                    BigDecimal balance = f.amountDue().subtract(f.amountPaid());
                    addRow(table, formatDate(f.dueDate()), formatCurrency(f.amountDue()),
                            formatCurrency(f.amountPaid()),
                            formatCurrency(balance), f.status().name().replace('_', ' '));
                }
                document.add(table);
            }

            addGeneratedFooter(document);
            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new UncheckedIOException("Failed to generate report card PDF", new java.io.IOException(e));
        }
    }

    // ---- Layout helpers -----------------------------------------------------

    private Document newDocument() {
        return new Document(PageSize.A4, 40, 40, 50, 50);
    }

    private void addTitleBlock(Document document, String tenantName, String subtitle, String contextLine)
            throws DocumentException {
        Paragraph title = new Paragraph(tenantName, TITLE_FONT);
        title.setSpacingAfter(2f);
        document.add(title);

        Paragraph sub = new Paragraph(subtitle, SUBTITLE_FONT);
        sub.setSpacingAfter(2f);
        document.add(sub);

        Paragraph context = new Paragraph(contextLine, META_FONT);
        context.setSpacingAfter(16f);
        document.add(context);
    }

    private void addSectionHeading(Document document, String text) throws DocumentException {
        Paragraph heading = new Paragraph(text, SECTION_FONT);
        heading.setSpacingBefore(14f);
        heading.setSpacingAfter(8f);
        document.add(heading);
    }

    private record Stat(String label, String value, String sub) {
    }

    private Stat stat(String label, String value) {
        return new Stat(label, value, null);
    }

    private Stat stat(String label, String value, String sub) {
        return new Stat(label, value, sub);
    }

    private void addStatRow(Document document, Stat... stats) throws DocumentException {
        PdfPTable table = new PdfPTable(stats.length);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);
        for (Stat s : stats) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(new java.awt.Color(220, 220, 220));
            cell.setPadding(8f);
            Paragraph label = new Paragraph(s.label(), STAT_LABEL_FONT);
            Paragraph value = new Paragraph(s.value(), STAT_VALUE_FONT);
            value.setSpacingBefore(2f);
            cell.addElement(label);
            cell.addElement(value);
            if (s.sub() != null) {
                Paragraph sub = new Paragraph(s.sub(), META_FONT);
                sub.setSpacingBefore(1f);
                cell.addElement(sub);
            }
            table.addCell(cell);
        }
        document.add(table);
    }

    private PdfPTable newTable(int columns, float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(columns);
        table.setWidths(widths);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);
        return table;
    }

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Paragraph(h, TABLE_HEADER_FONT));
            cell.setBackgroundColor(HEADER_ROW_COLOR);
            cell.setPadding(5f);
            table.addCell(cell);
        }
    }

    private void addRow(PdfPTable table, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Paragraph(v, TABLE_CELL_FONT));
            cell.setPadding(5f);
            table.addCell(cell);
        }
    }

    private Paragraph smallNote(String text) {
        Paragraph p = new Paragraph(text, META_FONT);
        p.setSpacingAfter(10f);
        return p;
    }

    private void addGeneratedFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "Generated " + GENERATED_AT_FORMAT.format(java.time.Instant.now().atZone(ZoneId.of("Asia/Kolkata")))
                        + " IST",
                META_FONT);
        footer.setSpacingBefore(18f);
        footer.setAlignment(Element.ALIGN_RIGHT);
        document.add(footer);
    }

    private Tenant currentTenant() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private String batchName(UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return batchRepository.findByIdAndTenantId(batchId, tenantId).map(Batch::getName).orElse(null);
    }

    private String formatDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
    }

    private String formatCurrency(BigDecimal amount) {
        // NOT the \u20b9 Rupee sign here - that glyph doesn't exist in the
        // WinAnsi/CP1252 encoding OpenPDF's base Helvetica font uses, so it
        // would render as a blank box in the actual PDF rather than the
        // symbol. "Rs." is the safe, universally-supported equivalent
        // without needing to embed a Unicode font just for one character.
        return "Rs. " + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
