package com.teacheros.modules.imports;

import com.teacheros.common.exception.ApiException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a .csv or .xlsx upload into plain rows of text, with the line number
 * each row has in the person's own file so errors can point at it. Knows
 * nothing about students - that's StudentImportService's job.
 *
 * Only the first sheet of a workbook is read, and only .xlsx (not the old
 * binary .xls). Blank rows are skipped but still counted for line numbers.
 */
final class TabularFileReader {

    record RawRow(int lineNumber, List<String> cells) {
    }

    record ParsedFile(List<String> headers, List<RawRow> rows) {
    }

    private TabularFileReader() {
    }

    static ParsedFile read(MultipartFile file, int maxRows) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return readCsv(file, maxRows);
            }
            if (name.endsWith(".xlsx")) {
                return readXlsx(file, maxRows);
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_UNREADABLE",
                    "Could not read that file. Make sure it's a valid .csv or .xlsx file.");
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMPORT_FORMAT",
                "Upload a .csv or .xlsx file. For an old .xls file, open it and use Save As .xlsx or .csv.");
    }

    private static ParsedFile readCsv(MultipartFile file, int maxRows) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1); // BOM added by Excel's "CSV UTF-8"
        }

        // Empty lines are kept as records (not ignored) so the record index
        // equals the physical line number for ordinary files.
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(detectDelimiter(text))
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .build();

        List<String> headers = null;
        List<RawRow> rows = new ArrayList<>();
        int recordIndex = 0;
        try (CSVParser parser = format.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                recordIndex++;
                List<String> cells = record.toList();
                if (isBlank(cells)) {
                    continue;
                }
                if (headers == null) {
                    headers = cells;
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw tooManyRows(maxRows);
                }
                rows.add(new RawRow(recordIndex, cells));
            }
        }
        return finish(headers, rows);
    }

    private static ParsedFile readXlsx(MultipartFile file, int maxRows) throws IOException {
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw emptyFile();
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            List<String> headers = null;
            List<RawRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    // DataFormatter gives the text as Excel displays it, so a
                    // phone typed as a number comes out as "9876543210", not 9.87E9.
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim());
                }
                if (isBlank(cells)) {
                    continue;
                }
                if (headers == null) {
                    headers = cells;
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw tooManyRows(maxRows);
                }
                rows.add(new RawRow(row.getRowNum() + 1, cells));
            }
            return finish(headers, rows);
        }
    }

    private static ParsedFile finish(List<String> headers, List<RawRow> rows) {
        if (headers == null) {
            throw emptyFile();
        }
        return new ParsedFile(headers, rows);
    }

    private static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String firstLine = end == -1 ? text : text.substring(0, end);
        long commas = firstLine.chars().filter(ch -> ch == ',').count();
        long semicolons = firstLine.chars().filter(ch -> ch == ';').count();
        long tabs = firstLine.chars().filter(ch -> ch == '\t').count();
        if (semicolons > commas && semicolons >= tabs) {
            return ';';
        }
        if (tabs > commas) {
            return '\t';
        }
        return ',';
    }

    private static boolean isBlank(List<String> cells) {
        return cells.stream().allMatch(c -> c == null || c.isBlank());
    }

    private static ApiException emptyFile() {
        return new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_EMPTY", "That file is empty.");
    }

    private static ApiException tooManyRows(int maxRows) {
        return new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_ROWS",
                "That file has more than " + maxRows + " student rows. Split it into smaller files.");
    }
}
