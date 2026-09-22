import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { Student } from '@/lib/types'

/**
 * Resolves "which student(s) does this logged-in account see" for the
 * STUDENT and PARENT roles. A student always has exactly one (themself,
 * via /students/me); a parent may have several (via /students/me/children).
 * Every student/parent section page (timetable, homework, tests, etc.)
 * needs this same resolution + active-child switching, so it lives here
 * once instead of being copy-pasted into every page.
 */
export function useLinkedStudents() {
  const { role } = useAuth()
  const [students, setStudents] = useState<Student[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    const req: Promise<Student[]> =
      role === 'PARENT'
        ? api.get<Student[]>('/api/v1/students/me/children')
        : api.get<Student>('/api/v1/students/me').then((s) => [s])

    req
      .then((list) => {
        setStudents(list)
        setActiveId((prev) => (prev && list.some((s) => s.id === prev) ? prev : list[0]?.id ?? null))
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your student record.'))
      .finally(() => setLoading(false))
  }, [role])

  const activeStudent = students.find((s) => s.id === activeId) ?? null
  return { students, activeId, setActiveId, activeStudent, loading, error }
}
