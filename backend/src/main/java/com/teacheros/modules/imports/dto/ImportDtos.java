package com.teacheros.modules.imports.dto;

import java.util.List;

public class ImportDtos {

    /**
     * One bad row. lineNumber is the row number as the person sees it in their file
     * (header = 1).
     */
    public record RowError(
            int lineNumber,
            List<String> messages) {
    }

    /**
     * importedRows is 0 for a dry run, and also 0 when errors blocked a real
     * import (errorRows > 0 and skipInvalidRows was false) - nothing is
     * saved in that case, so callers must check importedRows rather than
     * assuming a 200 means students were created.
     */
    public record ImportResult(
            boolean dryRun,
            int totalRows,
            int validRows,
            int importedRows,
            int errorRows,
            List<RowError> errors) {
    }
}
