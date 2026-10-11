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

// --- Homework submissions (Dev 7, V70) ---
// Mirrors backend/.../modules/homework/dto/HomeworkSubmissionDtos.java exactly.

export type SubmissionStatus = 'NOT_SUBMITTED' | 'SUBMITTED' | 'REVIEWED'

// status is NOT_SUBMITTED (and id/text/file/feedback fields null) when the
// student hasn't handed anything in. submissionsOpen is computed
// server-side (Asia/Kolkata, end of the due date).
export interface HomeworkSubmission {
  id: string | null
  homeworkId: string
  studentId: string
  studentName: string
  status: SubmissionStatus
  text: string | null
  originalFilename: string | null
  fileSizeBytes: number | null
  submittedAt: string | null
  teacherFeedback: string | null
  grade: string | null
  reviewedAt: string | null
  submissionsOpen: boolean
}

export interface ReviewSubmissionRequest {
  feedback?: string
  grade?: string
}
