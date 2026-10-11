// --- Announcements ---
// Mirrors backend/.../modules/announcement/dto/AnnouncementDtos.java exactly.

export type AnnouncementAudience = 'ALL' | 'BATCH' | 'CLASS'

export interface Announcement {
  id: string
  title: string
  body: string
  audience: AnnouncementAudience
  batchId: string | null
  grade: string | null
  createdByName: string
  createdAt: string
}

export interface FeedAnnouncement extends Announcement {
  read: boolean
}

export interface CreateAnnouncementRequest {
  title: string
  body: string
  audience: AnnouncementAudience
  batchId?: string
  grade?: string
}

export interface AnnouncementReadStats {
  announcementId: string
  readCount: number
  recipientCount: number
}
