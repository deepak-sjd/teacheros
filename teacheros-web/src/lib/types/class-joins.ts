// --- Live-class join tracking (Dev 7, V71) ---
// Mirrors backend/.../modules/timetable/dto/ClassJoinDtos.java exactly.

export interface ClassJoinResponse {
  recorded: boolean
  joinedAt: string | null
}

export interface JoinedStudent {
  studentId: string
  studentName: string
  joinedAt: string
}

export interface SessionJoinsResponse {
  slotId: string
  batchId: string
  sessionDate: string // ISO date
  subject: string | null
  startTime: string // "HH:mm:ss"
  endTime: string
  rosterSize: number
  joined: JoinedStudent[]
}
