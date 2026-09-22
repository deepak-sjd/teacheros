import { useAuth } from '@/lib/auth'
import { HomeworkPage } from '@/pages/teacher/Homework'
import { StudentHomework } from '@/pages/student/Homework'

export function HomeworkRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentHomework />
  return <HomeworkPage />
}
