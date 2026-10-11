export interface StudyMaterial {
  id: string
  batchId: string
  title: string
  description: string | null
  originalFilename: string
  contentType: string
  fileSizeBytes: number
  createdAt: string
}

export interface DownloadUrlResponse {
  url: string
  expiresInMinutes: number
}
