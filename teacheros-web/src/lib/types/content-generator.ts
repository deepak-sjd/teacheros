import type { QuestionInput } from './online-tests'

// --- AI Content Generator (teacher-facing) ---
// Mirrors backend/.../modules/content/dto/ContentGeneratorDtos.java
// exactly. GenerateQuestionsResponse.questions is field-for-field the
// same shape as QuestionInput by design, so a draft can be dropped
// straight into the Questions editor's own list with no reshaping.

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
