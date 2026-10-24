// Mirrors modules/compliance/dto/ComplianceDtos.java exactly.

export type ConsentState = 'PENDING' | 'GRANTED' | 'WITHDRAWN'

export interface ChildConsent {
  studentId: string
  fullName: string
  state: ConsentState
  textVersion: string | null
  grantedAt: string | null
  withdrawnAt: string | null
}

export interface ConsentStatusResponse {
  consentRequired: boolean
  currentTextVersion: string
  children: ChildConsent[]
}

export interface GrantConsentRequest {
  textVersion: string
  studentIds?: string[]
}

export interface RequestErasureRequest {
  reason?: string
}

export interface DecideErasureRequest {
  note?: string
}

export type ErasureStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'FAILED'

export interface ErasureRequestResponse {
  id: string
  studentId: string
  studentName: string
  status: ErasureStatus
  requestedByRole: string
  reason: string | null
  createdAt: string
  decidedAt: string | null
  decisionNote: string | null
  completedAt: string | null
  failureReason: string | null
}
