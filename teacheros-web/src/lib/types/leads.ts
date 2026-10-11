import type { Role } from './auth'

// --- Leads (staff pipeline) + public enquiry ---
// Mirrors backend/.../modules/lead/dto/LeadDtos.java exactly.

export type LeadStatus = 'NEW' | 'CONTACTED' | 'DEMO' | 'ENROLLED' | 'LOST'
export type LeadSource = 'WEBSITE' | 'COURSE_PAGE' | 'WALK_IN' | 'REFERRAL' | 'OTHER'

export interface Lead {
  id: string
  name: string
  phone: string
  email: string | null
  message: string | null
  source: LeadSource
  status: LeadStatus
  assignedTo: string | null
  assignedToName: string | null
  nextFollowUp: string | null // ISO date
  convertedStudentId: string | null
  createdAt: string
  updatedAt: string
}

// fromStatus === toStatus means a note-only entry (assignment / follow-up).
export interface LeadHistoryEntry {
  id: string
  fromStatus: LeadStatus | null
  toStatus: LeadStatus
  note: string | null
  changedByName: string | null
  changedAt: string
}

export interface LeadDetail {
  lead: Lead
  history: LeadHistoryEntry[]
}

// Every status is present (zero-filled by the backend).
export interface LeadCounts {
  counts: Record<LeadStatus, number>
}

export interface LeadAssignee {
  id: string
  displayName: string
  role: Role
}

export interface ConvertLeadResult {
  lead: Lead
  studentId: string
}

// schoolSlug is the school's PUBLIC slug, not a tenant id. website is a
// honeypot field - always sent empty by the real form.
export interface PublicEnquiryRequest {
  schoolSlug: string
  name: string
  phone: string
  email?: string
  courseId?: string
  message?: string
  source?: LeadSource
  website?: string
}
