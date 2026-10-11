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

// Mirrors RankingDtos.StudentRankResponse exactly - this is what
// GET /api/v1/tests/students/{id}/rank actually returns. rank is null and
// averagePercentage/gradedTestCount are 0 when the student has no graded
// marks yet or no batch assigned. Anonymous/aggregate-only by design -
// see the backend javadoc on why this is safe to show a PARENT/STUDENT
// while raw per-student marks for a test are staff-only.
export interface StudentRank {
  studentId: string
  batchId: string | null
  rank: number | null
  rankedStudentCount: number
  averagePercentage: number
  gradedTestCount: number
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
  rank: number | null
  averagePercentage: number
  gradedTestCount: number
}

export interface BatchRanking {
  batchId: string
  batchName: string
  rankedStudentCount: number
  entries: BatchRankingEntry[]
}
