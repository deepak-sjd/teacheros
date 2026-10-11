// --- Courses (staff) + public catalogue ---
// Mirrors backend/.../modules/course/dto/CourseDtos.java exactly.

export interface Course {
  id: string
  title: string
  description: string | null
  price: number
  syllabus: string | null
  demoVideoUrl: string | null
  batchId: string | null
  batchName: string | null
  published: boolean
  createdAt: string
  updatedAt: string
}

export interface SaveCourseRequest {
  title: string
  description?: string
  price: number
  syllabus?: string
  demoVideoUrl?: string
  batchId?: string
  published: boolean
}

// Public side: deliberately no batchId / published / timestamps.
export interface PublicSchool {
  name: string
  logoUrl: string | null
}

export interface PublicCourseSummary {
  id: string
  title: string
  shortDescription: string | null
  price: number
  hasDemoVideo: boolean
}

export interface PublicCourseDetail {
  id: string
  title: string
  description: string | null
  price: number
  syllabus: string | null
  demoVideoUrl: string | null
}
