import { useAuth } from '@/lib/auth'
import { Overview } from '@/pages/teacher/Overview'
import { MyChildren } from '@/pages/parent/MyChildren'
import { MyAttendance } from '@/pages/student/MyAttendance'

export function RoleHome() {
  const { role } = useAuth()

  if (role === 'PARENT') return <MyChildren />
  if (role === 'STUDENT') return <MyAttendance />
  return <Overview />
}
