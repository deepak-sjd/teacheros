// Mirrors backend/.../modules/academic/dto/PromotionDtos.java exactly.
// Preview and apply take the same body, so what was previewed is what is applied.

export type PromotionAction = 'PROMOTE' | 'DETAIN' | 'LEAVE'

export interface SectionMapping {
  fromSectionId: string
  toSectionId: string
}

export interface StudentActionOverride {
  studentId: string
  action: PromotionAction
}

export interface PromotionRequest {
  fromAcademicYearId: string
  toAcademicYearId?: string // omitted = next year by name, created on apply if missing
  sectionIds?: string[] // omitted = every section
  sectionMappings?: SectionMapping[]
  studentActions?: StudentActionOverride[]
  setNewYearCurrent?: boolean // only affects a year created by this call; default true
}

// issue is set when the student has no valid target - apply is refused until it is fixed.
export interface PromotionRow {
  studentId: string
  fullName: string
  rollNumber: string | null
  fromSectionId: string
  fromSectionName: string
  fromClassName: string
  toSectionId: string | null
  toSectionName: string | null
  toClassName: string | null
  action: PromotionAction
  note: string | null
  issue: string | null
}

export interface PromotionPreview {
  fromAcademicYearId: string
  fromAcademicYearName: string
  toAcademicYearId: string | null // null when the year doesn't exist yet
  toAcademicYearName: string
  toStartDate: string
  toEndDate: string
  toYearWillBeCreated: boolean
  promoteCount: number
  detainCount: number
  leaveCount: number
  issueCount: number
  rows: PromotionRow[]
  warnings: string[]
}

export interface PromotionResult {
  toAcademicYearId: string | null
  toAcademicYearName: string | null
  createdYear: boolean
  promoted: number
  detained: number
  left: number
}
