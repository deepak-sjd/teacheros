package com.teacheros.common.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Shared single-sheet Excel writer. Dev 10 (owner reports) and Dev 12
 * (finance reports) must both use this - do not add a second one. If Dev 10
 * merged an equivalent helper first, delete this file and point the Dev 12
 * exporter at theirs instead.
 *
 * Values: BigDecimal = money (Indian grouping, 2 decimals), Integer/Long =
 * whole number, LocalDate = dd-mmm-yyyy, anything else = text.
 *
 * Column widths are fixed on purpose: autoSizeColumn needs AWT fonts, which
 * the alpine JRE image in backend/Dockerfile does not have.
 *
 * Money is written as the BigDecimal's double value because an Excel cell
 * can only hold a double. All totals are summed in BigDecimal BEFORE they
 * reach this class; this is a display boundary only.
 */
public final class ExcelWorkbookBuilder implements AutoCloseable {

    // Lakh/crore grouping: 12,34,567.00
    private static final String INDIAN_MONEY = "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Sheet sheet;
    private final CellStyle text;
    private final CellStyle textBold;
    private final CellStyle money;
    private final CellStyle moneyBold;
    private final CellStyle whole;
    private final CellStyle wholeBold;
    private final CellStyle date;
    private final CellStyle dateBold;
    private final CellStyle header;
    private int rowIndex = 0;

    public ExcelWorkbookBuilder(String sheetName) {
        this.sheet = workbook.createSheet(sheetName);
        Font bold = workbook.createFont();
        bold.setBold(true);
        DataFormat fmt = workbook.createDataFormat();
        short moneyFmt = fmt.getFormat(INDIAN_MONEY);
        short dateFmt = fmt.getFormat("dd-mmm-yyyy");

        text = style(null, false, (short) 0);
        textBold = style(bold, false, (short) 0);
        money = style(null, false, moneyFmt);
        moneyBold = style(bold, false, moneyFmt);
        whole = style(null, false, (short) 1); // built-in "0"
        wholeBold = style(bold, false, (short) 1);
        date = style(null, false, dateFmt);
        dateBold = style(bold, false, dateFmt);
        header = style(bold, true, (short) 0);
    }

    private CellStyle style(Font font, boolean shaded, short format) {
        CellStyle s = workbook.createCellStyle();
        if (font != null) {
            s.setFont(font);
        }
        if (shaded) {
            s.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            s.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        }
        s.setDataFormat(format);
        return s;
    }

    public ExcelWorkbookBuilder title(String title) {
        Row r = sheet.createRow(rowIndex++);
        Cell c = r.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(textBold);
        rowIndex++; // blank line under the title
        return this;
    }

    public ExcelWorkbookBuilder headerRow(String... headers) {
        Row r = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(header);
            sheet.setColumnWidth(i, (i == 0 ? 30 : 18) * 256);
        }
        return this;
    }

    public ExcelWorkbookBuilder row(Object... values) {
        return write(false, values);
    }

    public ExcelWorkbookBuilder totalRow(Object... values) {
        return write(true, values);
    }

    public ExcelWorkbookBuilder blankRow() {
        rowIndex++;
        return this;
    }

    private ExcelWorkbookBuilder write(boolean bold, Object[] values) {
        Row r = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            Cell c = r.createCell(i);
            if (v == null) {
                c.setCellStyle(bold ? textBold : text);
            } else if (v instanceof BigDecimal bd) {
                c.setCellValue(bd.doubleValue());
                c.setCellStyle(bold ? moneyBold : money);
            } else if (v instanceof Integer || v instanceof Long) {
                c.setCellValue(((Number) v).doubleValue());
                c.setCellStyle(bold ? wholeBold : whole);
            } else if (v instanceof LocalDate d) {
                c.setCellValue(java.sql.Date.valueOf(d));
                c.setCellStyle(bold ? dateBold : date);
            } else {
                c.setCellValue(v.toString());
                c.setCellStyle(bold ? textBold : text);
            }
        }
        return this;
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }
}
