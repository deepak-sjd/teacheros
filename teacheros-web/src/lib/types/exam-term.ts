// --- Exam terms, grade scale, term report cards ---
// Mirrors backend/.../modules/reportcard/dto/ReportCardDtos.java exactly.

export interface ExamTerm {
  id: string
  academicYearId: string | null
  name: string
  startDate: string
  endDate: string
}

export interface GradeBand {
  grade: string
  minPercentage: number
  maxPercentage: number
  remark: string | null
}

// usingDefaults is true until the owner saves their own scale. Bands are highest-first.
export interface GradeScaleResponse {
  usingDefaults: boolean
  bands: GradeBand[]
}

export interface SubjectResult {
  subject: string
  averagePercentage: number
  gradedEntries: number
  grade: string | null
  remark: string | null
}

export interface TermResult {
  termId: string
  termName: string
  startDate: string
  endDate: string
  subjects: SubjectResult[]
  gradedEntries: number
  overallPercentage: number
  overallGrade: string | null
  overallRemark: string | null
  teacherRemark: string | null
}

export interface ReportCardRemark {
  studentId: string
  remark: string
}
