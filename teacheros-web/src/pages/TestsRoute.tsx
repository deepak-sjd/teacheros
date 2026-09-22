import { useAuth } from '@/lib/auth'
import { TestsPage } from '@/pages/teacher/Tests'
import { StudentTests } from '@/pages/student/Tests'

export function TestsRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentTests />
  return <TestsPage />
}
