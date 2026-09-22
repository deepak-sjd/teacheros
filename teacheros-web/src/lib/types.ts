export type Role = 'OWNER' | 'TEACHER' | 'STAFF' | 'STUDENT' | 'PARENT' | 'ADMIN'

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface JwtClaims {
  sub: string // userId
  tenantId: string
  role: Role
  iat: number
  exp: number
}

export interface Batch {
  id: string
  name: string
  subject: string | null
  scheduleSummary: string | null
}

export interface CreateBatchRequest {
  name: string
  subject?: string
  scheduleSummary?: string
}

export interface Student {
  id: string
  fullName: string
  grade: string | null
  guardianPhone: string
  phoneNumber: string | null
  batchId: string | null
}

export interface CreateStudentRequest {
  fullName: string
  grade?: string
  guardianPhone: string
  phoneNumber?: string
  batchId?: string
}

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

export interface AttendanceSummaryResponse {
  studentId: string
  sessionsRecorded: number
  sessionsAttended: number
  attendancePercentage: number
}

export type FeeStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'OVERDUE'

export interface Fee {
  id: string
  studentId: string
  amountDue: number
  amountPaid: number
  dueDate: string
  status: FeeStatus
}

export interface CreateFeeRequest {
  studentId: string
  amountDue: number
  dueDate: string
}

export interface RecordPaymentRequest {
  amountPaid: number
  method?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface StudyMaterial {
  id: string
  batchId: string
  title: string
  description: string | null
  originalFilename: string
  contentType: string
  fileSizeBytes: number
  createdAt: string
}

export interface DownloadUrlResponse {
  url: string
  expiresInMinutes: number
}

export interface Homework {
  id: string
  batchId: string
  title: string
  description: string | null
  dueDate: string
  createdAt: string
  updatedAt: string
}

export interface CreateHomeworkRequest {
  batchId: string
  title: string
  description?: string
  dueDate: string
}

export interface UpdateHomeworkRequest {
  title: string
  description?: string
  dueDate: string
}

export interface TimetableSlot {
  id: string
  batchId: string
  dayOfWeek: number // 1=Mon .. 7=Sun
  startTime: string // "HH:mm:ss"
  endTime: string
  subject: string | null
}

export type NotificationType = 'LOW_ATTENDANCE' | 'FEE_DUE' | 'FEE_OVERDUE'

export interface AppNotification {
  id: string
  studentId: string
  type: NotificationType
  title: string
  message: string
  read: boolean
  createdAt: string
}

export interface GenerateResult {
  created: number
}

export type SenderRole = 'PARENT' | 'STAFF'

export interface Message {
  id: string
  senderRole: SenderRole
  senderName: string
  body: string
  mine: boolean
  createdAt: string
}

export interface Assessment {
  id: string
  batchId: string
  title: string
  subject: string | null
  maxMarks: number
  testDate: string
  createdAt: string
  updatedAt: string
}

export interface CreateAssessmentRequest {
  batchId: string
  title: string
  subject?: string
  maxMarks: number
  testDate: string
}

export interface UpdateAssessmentRequest {
  title: string
  subject?: string
  maxMarks: number
  testDate: string
}

export interface StudentMarkEntry {
  studentId: string
  marksObtained: number | null
}

export interface MarkResponse {
  studentId: string
  marksObtained: number | null
}

export interface StudentMarkHistoryEntry {
  testId: string
  testTitle: string
  subject: string | null
  maxMarks: number
  marksObtained: number | null
  testDate: string
}
