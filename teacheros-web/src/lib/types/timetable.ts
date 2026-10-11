export type MeetingPlatform = 'ZOOM' | 'GOOGLE_MEET' | 'MICROSOFT_TEAMS' | 'OTHER'

export interface TimetableSlot {
  id: string
  batchId: string
  dayOfWeek: number // 1=Mon .. 7=Sun
  startTime: string // "HH:mm:ss"
  endTime: string
  subject: string | null
  meetingLink: string | null
  meetingPlatform: MeetingPlatform | null
}

// A blank/omitted meetingLink clears the link (and platform) entirely -
// see TimetableService#updateMeetingLink on the backend.
export interface UpdateMeetingLinkRequest {
  meetingLink?: string
  meetingPlatform?: MeetingPlatform
}
