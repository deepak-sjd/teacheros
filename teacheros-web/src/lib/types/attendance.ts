export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED'

export interface StudentAttendanceRecord {
  studentId: string
  status: AttendanceStatus
}

export interface MarkAttendanceRequest {
  batchId: string
  sessionDate: string // ISO date
  entries: { studentId: string; status: AttendanceStatus }[]
}

export interface QrTokenResponse {
  token: string
  expiresAt: string
}

export interface AttendanceSummaryResponse {
  studentId: string
  sessionsRecorded: number
  sessionsAttended: number
  attendancePercentage: number
}

// Mirrors AttendanceDtos.BatchSessionSummary exactly.
export interface BatchSessionSummary {
  classSessionId: string
  sessionDate: string // ISO date
  presentCount: number
  absentCount: number
  lateCount: number
  excusedCount: number
  totalCount: number
}
