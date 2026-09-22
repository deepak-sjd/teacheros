import { useAuth } from '@/lib/auth'
import { TimetablePage } from '@/pages/teacher/Timetable'
import { StudentTimetable } from '@/pages/student/Timetable'

export function TimetableRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentTimetable />
  return <TimetablePage />
}
