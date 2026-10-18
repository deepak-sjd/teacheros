import { useAuth } from '@/lib/auth'
import { StudentLeave } from '@/pages/student/Leave'
import { LeaveRequestsPage } from '@/pages/teacher/LeaveRequests'

// Same path, different page by role (same pattern as AnnouncementsRoute):
// parents/students request leave, staff decide.
export function LeaveRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentLeave />
  return <LeaveRequestsPage />
}
