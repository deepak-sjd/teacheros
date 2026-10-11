package com.teacheros.modules.payment;

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
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeePayment;
import com.teacheros.modules.fee.FeePaymentRepository;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.payment.dto.ReceiptDtos.PaymentReceiptResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.Tenant;
import com.teacheros.modules.tenant.TenantRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Per-payment receipts. Every entry point resolves the payment/fee inside
 * the caller's tenant and then runs StudentAccessGuard, so a parent can
 * only reach their own family's receipts (anything else is a 404).
 * Styling mirrors ReportPdfService (same fonts, same "Rs." rule).
 */
@Service
public class ReceiptService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.ITALIC);
    private static final Font META_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, new java.awt.Color(90, 90, 90));
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9,
            new java.awt.Color(90, 90, 90));
    private static final Font VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

    private final FeePaymentRepository feePaymentRepository;
    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final TenantRepository tenantRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public ReceiptService(FeePaymentRepository feePaymentRepository, FeeRepository feeRepository,
            StudentRepository studentRepository, TenantRepository tenantRepository,
            StudentAccessGuard studentAccessGuard, CurrentTenantProvider currentTenantProvider) {
        this.feePaymentRepository = feePaymentRepository;
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
        this.tenantRepository = tenantRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<PaymentReceiptResponse> listForFee(UUID feeId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Fee fee = feeRepository.findByIdAndTenantId(feeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee", feeId));
        studentAccessGuard.assertCanView(fee.getStudentId());

        return feePaymentRepository.findByTenantIdAndFeeId(tenantId, feeId).stream()
                .sorted(Comparator.comparing(FeePayment::getPaidAt))
                .map(p -> new PaymentReceiptResponse(p.getId(), receiptNumber(p), p.getAmountPaid(),
                        p.getPaidAt(), p.getMethod()))
                .toList();
    }

    public byte[] generatePdf(UUID paymentId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        FeePayment payment = feePaymentRepository.findByIdAndTenantId(paymentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        Fee fee = feeRepository.findByIdAndTenantId(payment.getFeeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        // Guard BEFORE reading anything else - another family's receipt is a 404.
        studentAccessGuard.assertCanView(fee.getStudentId());

        Student student = studentRepository.findByIdAndTenantId(fee.getStudentId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        Document document = new Document(PageSize.A5, 40, 40, 50, 50);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph(tenant.getName(), TITLE_FONT);
            title.setSpacingAfter(2f);
            document.add(title);
            Paragraph sub = new Paragraph("Fee Receipt", SUBTITLE_FONT);
            sub.setSpacingAfter(16f);
            document.add(sub);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 1.2f, 2f });
            addRow(table, "Receipt number", receiptNumber(payment));
            addRow(table, "Date", DATE_TIME.format(payment.getPaidAt().atZone(APP_ZONE)));
            addRow(table, "Student", student.getFullName()
                    + (student.getGrade() != null ? " (Grade " + student.getGrade() + ")" : ""));
            addRow(table, "Towards fee due", DATE.format(fee.getDueDate()));
            addRow(table, "Payment method", payment.getMethod() != null ? payment.getMethod() : "Not specified");
            addRow(table, "Amount paid",
                    "Rs. " + payment.getAmountPaid().setScale(2, RoundingMode.HALF_UP).toPlainString());
            document.add(table);

            Paragraph footer = new Paragraph("This is a computer-generated receipt.", META_FONT);
            footer.setSpacingBefore(24f);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new UncheckedIOException("Failed to generate receipt PDF", new java.io.IOException(e));
        }
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, LABEL_FONT));
        labelCell.setBorderColor(new java.awt.Color(220, 220, 220));
        labelCell.setPadding(8f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value, VALUE_FONT));
        valueCell.setBorderColor(new java.awt.Color(220, 220, 220));
        valueCell.setPadding(8f);
        table.addCell(valueCell);
    }

    private String receiptNumber(FeePayment payment) {
        return payment.getReceiptNumber() == null
                ? "RCP-PENDING"
                : "RCP-%06d".formatted(payment.getReceiptNumber());
    }
}
