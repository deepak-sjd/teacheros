// Mirrors StudentProfileDtos and LeaveRequestDtos on the backend exactly.

export interface StudentProfileExtras {
  studentId: string
  previousSchool: string | null
  // null unless the caller is staff or the student's own guardian.
  medicalNotes: string | null
  // false means "you may not see medical notes" (not "there are none").
  medicalNotesVisible: boolean
  bloodGroup: string | null
  address: string | null
  guardian2Name: string | null
  guardian2Phone: string | null
  guardian2Relation: string | null
}

// Full replace on PUT - send every field, null to clear.
export interface UpdateStudentProfileRequest {
  previousSchool: string | null
  medicalNotes: string | null
  bloodGroup: string | null
  address: string | null
  guardian2Name: string | null
  guardian2Phone: string | null
  guardian2Relation: string | null
}

export type StudentDocumentType =
  | 'BIRTH_CERTIFICATE'
  | 'TRANSFER_CERTIFICATE'
  | 'REPORT_CARD'
  | 'ID_PROOF'
  | 'MEDICAL'
  | 'PHOTO'
  | 'OTHER'

export interface StudentDocument {
  id: string
  studentId: string
  docType: StudentDocumentType
  title: string
  originalFilename: string
  contentType: string
  fileSizeBytes: number
  createdAt: string
}

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface LeaveRequest {
  id: string
  studentId: string
  studentName: string
  fromDate: string // ISO date
  toDate: string // ISO date
  reason: string
  status: LeaveStatus
  createdAt: string
  decidedAt: string | null
  decisionNote: string | null
}

export interface SubmitLeaveRequest {
  fromDate: string
  toDate: string
  reason: string
}

export interface DecideLeaveRequest {
  decision: 'APPROVED' | 'REJECTED'
  note?: string
}
