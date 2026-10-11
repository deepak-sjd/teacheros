// --- Bulk student import ---
// Mirrors backend/.../modules/imports/dto/ImportDtos.java exactly.
// importedRows is 0 for a dry run, and also 0 when errors blocked a real
// import - check it rather than assuming a 200 means students were created.

export interface ImportRowError {
  lineNumber: number
  messages: string[]
}

export interface ImportResult {
  dryRun: boolean
  totalRows: number
  validRows: number
  importedRows: number
  errorRows: number
  errors: ImportRowError[]
}
