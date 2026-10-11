// --- Course content and learning (Dev 7, V72) ---
// Mirrors backend/.../modules/course/dto/CourseContentDtos.java exactly.
// TS names differ from the Java record names in three places:
//   LearnCourse -> LearnCourseResponse, LessonDetail -> LessonDetailResponse,
//   LessonProgressResponse -> ProgressResponse. Field names match exactly.

export type LessonType = 'VIDEO' | 'NOTES' | 'LINK'

export interface StaffLesson {
  id: string
  chapterId: string
  title: string
  type: LessonType
  videoProvider: string | null
  videoRef: string | null
  notesText: string | null
  linkUrl: string | null
  durationSeconds: number | null
  position: number
}

export interface StaffChapter {
  id: string
  courseId: string
  title: string
  position: number
  lessons: StaffLesson[]
}

export interface EnrolledCourseSummary {
  courseId: string
  title: string
  description: string | null
  totalLessons: number
  completedLessons: number
}

// No video reference here - playback only goes through /playback.
export interface LessonSummary {
  id: string
  title: string
  type: LessonType
  durationSeconds: number | null
  position: number
  completed: boolean
  positionSeconds: number
}

export interface StudentChapter {
  id: string
  title: string
  position: number
  lessons: LessonSummary[]
}

export interface LearnCourse {
  courseId: string
  title: string
  description: string | null
  totalLessons: number
  completedLessons: number
  chapters: StudentChapter[]
}

export interface LessonDetail {
  id: string
  courseId: string
  chapterId: string
  title: string
  type: LessonType
  notesText: string | null
  linkUrl: string | null
  durationSeconds: number | null
  positionSeconds: number
  completed: boolean
}

export interface PlaybackResponse {
  url: string
  expiresInMinutes: number
}

export interface LessonProgressResponse {
  lessonId: string
  positionSeconds: number
  completed: boolean
}
