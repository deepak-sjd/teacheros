import { useAuth } from '@/lib/auth'
import { AlertsPage } from '@/pages/teacher/Alerts'
import { StudentAlerts } from '@/pages/student/Alerts'

export function AlertsRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentAlerts />
  return <AlertsPage />
}
