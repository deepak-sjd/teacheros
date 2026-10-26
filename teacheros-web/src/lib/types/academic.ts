// Mirrors backend/.../modules/academic/dto/AcademicDtos.java exactly.
// SCHOOL tenants only - every /api/v1/academic endpoint returns 403 SCHOOL_ONLY otherwise.

export interface AcademicYear {
  id: string
  name: string // e.g. "2026-27"
  startDate: string // ISO date
  endDate: string
  current: boolean
}

export interface CreateAcademicYearRequest {
  name: string
  startDate: string
  endDate: string
  current?: boolean // the very first year is always current
}

export interface UpdateAcademicYearRequest {
  name: string
  startDate: string
  endDate: string
}

export interface SchoolClass {
  id: string
  name: string
  displayOrder: number
  active: boolean
}

export interface CreateClassRequest {
  name: string
  displayOrder?: number
}

// Full replace. active omitted = unchanged.
export interface UpdateClassRequest {
  name: string
  displayOrder?: number
  active?: boolean
}

export interface Section {
  id: string
  classId: string
  className: string
  name: string
  classTeacherUserId: string | null
  classTeacherName: string | null
  capacity: number | null
  studentCount: number
  active: boolean
}

export interface CreateSectionRequest {
  classId: string
  name: string
  classTeacherUserId?: string
  capacity?: number
}

// Full replace: omitting classTeacherUserId clears the class teacher,
// omitting capacity removes the limit. active omitted = unchanged.
export interface UpdateSectionRequest {
  name: string
  classTeacherUserId?: string
  capacity?: number
  active?: boolean
}

export interface Subject {
  id: string
  name: string
  code: string | null
  active: boolean
}

export interface CreateSubjectRequest {
  name: string
  code?: string
}

export interface UpdateSubjectRequest {
  name: string
  code?: string
  active?: boolean
}

export interface AssignTeacherRequest {
  teacherUserId: string
}

export interface SubjectTeacher {
  subjectId: string
  subjectName: string
  subjectCode: string | null
  teacherUserId: string
  teacherName: string | null
}

export interface PlaceStudentRequest {
  sectionId: string
  rollNumber?: string
  academicYearId?: string // omitted = the current year
}

export interface StudentPlacement {
  studentId: string
  fullName: string
  sectionId: string
  sectionName: string
  classId: string
  className: string | null
  academicYearId: string | null
  academicYearName: string | null
  rollNumber: string | null
}
