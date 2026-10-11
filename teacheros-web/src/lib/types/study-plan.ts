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
