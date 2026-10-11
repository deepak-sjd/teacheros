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

// --- Teacher-facing online-test results ---
// Mirrors backend AttemptDtos.StudentResultRow/QuestionStat/
// TestResultsResponse exactly.

export interface StudentResultRow {
  studentId: string
  status: 'IN_PROGRESS' | 'SUBMITTED'
  submittedAt: string | null
  score: number | null
  maxMarks: number
}

export interface QuestionStat {
  questionId: string
  questionText: string
  correctOption: string
  correctCount: number
  incorrectCount: number
  unansweredCount: number
  mostCommonWrongOption: string | null
}

export interface TestResultsResponse {
  testId: string
  title: string
  maxMarks: number
  students: StudentResultRow[]
  questions: QuestionStat[]
}
