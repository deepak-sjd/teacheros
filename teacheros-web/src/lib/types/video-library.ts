// --- Video library (Dev 7, V73) ---
// Mirrors backend/.../modules/course/dto/VideoUploadDtos.java exactly.
// TS names differ from the Java records in two places: `VideoUpload` is
// `VideoUploadResponse` in Java, and `VideoLibrary` is `VideoLibraryResponse`.
// Field names match exactly.

export type VideoUploadStatus = 'PENDING_UPLOAD' | 'PROCESSING' | 'READY' | 'FAILED'

// Staff-only: includes the provider reference, which students never get.
export interface VideoUpload {
  id: string
  title: string
  status: VideoUploadStatus
  sizeBytes: number
  durationSeconds: number | null
  failureReason: string | null
  provider: string
  videoRef: string
  createdAt: string
}

export interface VideoUploadCredentials {
  tusEndpoint: string
  libraryId: string
  videoId: string
  authorizationExpire: number
  authorizationSignature: string
}

// credentials is null for the dev "log" provider: there is nothing to upload.
export interface CreateVideoUploadResponse {
  upload: VideoUpload
  credentials: VideoUploadCredentials | null
}

export interface VideoLibrary {
  usedBytes: number
  limitBytes: number
  uploads: VideoUpload[]
}
