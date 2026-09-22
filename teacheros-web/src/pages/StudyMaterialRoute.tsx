import { useAuth } from '@/lib/auth'
import { StudyMaterialPage } from '@/pages/teacher/StudyMaterial'
import { StudentStudyMaterial } from '@/pages/student/StudyMaterial'

export function StudyMaterialRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentStudyMaterial />
  return <StudyMaterialPage />
}
