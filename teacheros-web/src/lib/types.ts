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
  guardianEmail: string | null
  batchId: string | null
}

export interface CreateStudentRequest {
  fullName: string
  grade?: string
  guardianPhone: string
  phoneNumber?: string
  guardianEmail?: string
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

export type MeetingPlatform = 'ZOOM' | 'GOOGLE_MEET' | 'MICROSOFT_TEAMS' | 'OTHER'

export interface TimetableSlot {
  id: string
  batchId: string
  dayOfWeek: number // 1=Mon .. 7=Sun
  startTime: string // "HH:mm:ss"
  endTime: string
  subject: string | null
  meetingLink: string | null
  meetingPlatform: MeetingPlatform | null
}

// A blank/omitted meetingLink clears the link (and platform) entirely -
// see TimetableService#updateMeetingLink on the backend.
export interface UpdateMeetingLinkRequest {
  meetingLink?: string
  meetingPlatform?: MeetingPlatform
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

export type TestType = 'OFFLINE' | 'ONLINE_MCQ'

export interface Assessment {
  id: string
  batchId: string
  title: string
  subject: string | null
  maxMarks: number
  testDate: string
  testType: TestType
  durationMinutes: number | null
  availableFrom: string | null
  availableUntil: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateAssessmentRequest {
  batchId: string
  title: string
  subject?: string
  maxMarks: number
  testDate: string
  testType?: TestType
  durationMinutes?: number
  availableFrom?: string
  availableUntil?: string
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

// Mirrors MarkDtos.StudentRankResponse. rank/percentage are null when the
// student has no graded marks yet in their batch - totalStudents is still
// populated either way. Anonymous/aggregate-only by design - see the
// backend javadoc on why this is safe to show a PARENT/STUDENT while raw
// per-student marks for a test are staff-only.
export interface StudentRank {
  totalMarksObtained: number
  totalMaxMarks: number
  percentage: number | null
  rank: number | null
  totalStudents: number
}

// Mirrors RankingDtos.StudentRankResponse exactly (the live endpoint -
// NOT MarkDtos.StudentRankResponse, an earlier unused draft of this same
// feature left behind in the backend with a different field shape).
// rank/rankedStudentCount are null/0 when the student has no graded marks
// yet, or no batch assigned.
//
// NOTE (merge): in the new code this interface was also named `StudentRank`,
// which clashes with the old `StudentRank` above. To keep the old one intact,
// this one is exported as `StudentRankLive`.
export interface StudentRankLive {
  studentId: string
  batchId: string | null
  rank: number | null
  rankedStudentCount: number
  averagePercentage: number
  gradedTestCount: number
}

// --- Messages (parent-teacher messaging) ---
// Mirrors backend/.../modules/message/dto/MessageDtos.java exactly.

export type SenderRole = 'PARENT' | 'STAFF'

export interface Message {
  id: string
  senderRole: SenderRole
  senderName: string
  body: string
  // Whether the CALLER's side sent this message, resolved server-side -
  // see MessageDtos.MessageResponse's javadoc. Lets the UI align bubbles
  // without needing to know the viewer's own role.
  mine: boolean
  createdAt: string
}

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

// --- Analytics dashboard ---
// Mirrors backend/.../modules/analytics/dto/AnalyticsDtos.java exactly.
// Unlike ReportSummary (a point-in-time table for a chosen range), these
// are shaped specifically for the Overview page's two charts.

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
// schema (see FeeReportSummary's comment above), so this is always
// tenant-wide.
export interface FeeCollectionTrendResponse {
  from: string
  to: string
  points: FeeCollectionPoint[]
}


// --- AI doubt solving ---

export type DoubtSender = 'STUDENT' | 'AI'

export interface DoubtMessage {
  id: string
  sender: DoubtSender
  body: string
  createdAt: string
}

export interface AskDoubtRequest {
  question: string
}

export interface DoubtThreadResponse {
  messages: DoubtMessage[]
  questionsAskedToday: number
  dailyQuestionLimit: number
}

export interface AskDoubtResponse {
  question: DoubtMessage
  answer: DoubtMessage
}

// ---- Online MCQ tests ----

export interface QuestionInput {
  questionText: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctOption: 'A' | 'B' | 'C' | 'D'
  marks: number
}

export interface QuestionResponse extends QuestionInput {
  id: string
  position: number
}

export interface QuestionForStudent {
  id: string
  questionText: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  marks: number
  position: number
}

export interface StartTestResponse {
  attemptId: string
  testId: string
  title: string
  durationMinutes: number | null
  startedAt: string
  softDeadline: string | null
  questions: QuestionForStudent[]
}

export type AttemptStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED'

export interface MyAttemptResponse {
  status: AttemptStatus
  startedAt: string | null
  softDeadline: string | null
  score: number | null
  maxMarks: number
}

export interface AnswerResult {
  questionId: string
  questionText: string
  selectedOption: string | null
  correctOption: string
  correct: boolean
  marksAwarded: number
  marksPossible: number
}

export interface SubmitResultResponse {
  score: number
  maxMarks: number
  breakdown: AnswerResult[]
}

// --- AI Content Generator (teacher-facing) ---
// Mirrors backend/.../modules/content/dto/ContentGeneratorDtos.java
// exactly. GenerateQuestionsResponse.questions is field-for-field the
// same shape as QuestionInput (above) by design, so a draft can be
// dropped straight into the Questions editor's own list with no reshaping.

export interface GenerateQuestionsRequest {
  topic: string
  subject?: string
  grade?: string
  difficulty?: string
  count: number
}

export interface GenerateQuestionsResponse {
  questions: QuestionInput[]
}

export interface GenerateNotesRequest {
  topic: string
  subject?: string
  grade?: string
}

export interface GenerateNotesResponse {
  notes: string
}


// --- Payment gateway (fee payments via Razorpay or the free mock provider) ---
// Mirrors backend/.../modules/payment/dto/PaymentDtos.java exactly.

export interface CreateOrderResponse {
  // "mock": no real gateway configured - frontend shows its own "Simulate
  // successful payment" button instead of opening Razorpay Checkout.
  // "razorpay": real gateway - frontend loads checkout.js and opens it
  // using keyId below.
  provider: 'mock' | 'razorpay'
  orderId: string
  amountInPaise: number
  currency: string
  // Razorpay's PUBLIC key - safe to expose to the frontend. Null in mock
  // mode, where it's never used.
  keyId: string | null
  feeId: string
}

export interface VerifyPaymentRequest {
  feeId: string
  orderId: string
  paymentId: string
  // Blank/omitted in mock mode - there's no real signature to check since
  // no real money or real Razorpay order is involved.
  signature?: string
}


// --- Class ranking (staff-only leaderboard) ---
// Mirrors backend/.../modules/assessment/dto/RankingDtos.java exactly.
// The staff-only counterpart to StudentRank above: same sum-of-marks
// percentage and standard-competition-ranking math, but as a full named
// leaderboard for a batch instead of one student's own anonymous standing.
// Never call this from a PARENT/STUDENT account - use StudentRank instead.

export interface BatchRankingEntry {
  studentId: string
  studentName: string
  totalMarksObtained: number
  totalMaxMarks: number
  percentage: number | null
  rank: number | null
}

export interface BatchRanking {
  batchId: string
  batchName: string
  totalStudents: number
  entries: BatchRankingEntry[]
}

// --- Weak Topic Detection + Study Plan ---
// Mirrors backend/.../modules/studyplan/dto/StudyPlanDtos.java exactly.

export interface SubjectBreakdown {
  subject: string
  averagePercentage: number
  gradedEntries: number
  weak: boolean
}

export interface StudyPlanResponse {
  subjects: SubjectBreakdown[]
  planText: string | null
  generatedAt: string | null
}

// --- Settings: my profile + tenant (school) settings ---
// Mirrors backend/.../modules/user/dto/UserDtos.java and
// modules/tenant/dto/TenantDtos.java exactly.

export interface MyProfileResponse {
  id: string
  phoneNumber: string
  displayName: string
  email: string | null
  role: Role
  tenantName: string | null
}

// phoneNumber is deliberately not here - it's the login identity (OTP
// target), not an editable contact field. See UserDtos.UpdateProfileRequest's
// javadoc for why changing it isn't a plain profile edit.
export interface UpdateProfileRequest {
  displayName: string
  email?: string
}

export interface TenantSettingsResponse {
  id: string
  name: string
  // Short-lived signed URL, same pattern as DownloadUrlResponse above -
  // null when no logo has been uploaded yet.
  logoUrl: string | null
}

export interface UpdateTenantRequest {
  name: string
}
