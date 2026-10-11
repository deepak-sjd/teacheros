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
