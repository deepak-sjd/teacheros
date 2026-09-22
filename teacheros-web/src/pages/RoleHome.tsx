import { useAuth } from '@/lib/auth'
import { Overview } from '@/pages/teacher/Overview'
import { StudentOverview } from '@/pages/student/Overview'

export function RoleHome() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentOverview />
  return <Overview />
}
