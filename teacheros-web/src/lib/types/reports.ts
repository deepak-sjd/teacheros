// --- Reports ---
// Mirrors backend/.../modules/report/dto/ReportDtos.java exactly. Nothing
// here is stored server-side - a report is computed fresh on every
// GET /api/v1/reports/summary call from existing attendance/fee/mark rows.

export interface BatchAttendanceRow {
  batchId: string
  batchName: string
  sessions: number
  presentCount: number
  attendancePercentage: number
}

export interface AttendanceReportSummary {
  totalSessions: number
  presentCount: number
  attendancePercentage: number
  byBatch: BatchAttendanceRow[]
}

// Tenant-wide, not per-batch - Fee has no batchId in the schema, so a
// per-batch fee breakdown isn't something the backend can honestly produce.
export interface FeeReportSummary {
  collectedInPeriod: number
  totalOutstanding: number
  pendingCount: number
  overdueCount: number
}

export interface BatchMarksRow {
  batchId: string
  batchName: string
  testsConducted: number
  gradedEntries: number
  averageScorePercentage: number
}

export interface MarksReportSummary {
  testsConducted: number
  gradedEntries: number
  averageScorePercentage: number
  byBatch: BatchMarksRow[]
}

export interface ReportSummary {
  from: string
  to: string
  batchId: string | null
  attendance: AttendanceReportSummary
  fees: FeeReportSummary
  marks: MarksReportSummary
}
