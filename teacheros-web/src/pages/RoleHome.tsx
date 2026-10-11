import { Navigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { Overview } from '@/pages/teacher/Overview'
import { StudentOverview } from '@/pages/student/Overview'

export function RoleHome() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentOverview />
  // The staff Overview fires attendance/marks/analytics calls these
  // permission-scoped roles are (correctly) denied, and its single Promise.all
  // would then render nothing. Send them to the first page they can use.
  if (role === 'ACCOUNTANT') return <Navigate to="/app/fees" replace />
  if (role === 'COUNSELLOR') return <Navigate to="/app/leads" replace />
  return <Overview />
}
