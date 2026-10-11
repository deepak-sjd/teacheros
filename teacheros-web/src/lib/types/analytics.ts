// --- Analytics dashboard ---
// Mirrors backend/.../modules/analytics/dto/AnalyticsDtos.java exactly.
// Unlike ReportSummary (a point-in-time table for a chosen range), these
// are shaped specifically for the Overview/Analytics pages' charts.

export interface AttendanceTrendPoint {
  date: string // ISO date
  sessionsRecorded: number
  sessionsAttended: number
  attendancePercentage: number
}

export interface AttendanceTrendResponse {
  from: string
  to: string
  batchId: string | null
  points: AttendanceTrendPoint[]
}

export interface SubjectPerformancePoint {
  subject: string
  gradedEntries: number
  averagePercentage: number
}

export interface SubjectPerformanceResponse {
  from: string
  to: string
  batchId: string | null
  points: SubjectPerformancePoint[]
}

export interface FeeCollectionPoint {
  date: string
  amountCollected: number
}

// No batchId, unlike the other two trends - fees have no batchId in the
// schema (see FeeReportSummary), so this is always tenant-wide.
export interface FeeCollectionTrendResponse {
  from: string
  to: string
  points: FeeCollectionPoint[]
}
