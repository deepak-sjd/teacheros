import { useAuth } from '@/lib/auth'
import { AnnouncementsPage } from '@/pages/teacher/Announcements'
import { StudentAnnouncements } from '@/pages/student/Announcements'

export function AnnouncementsRoute() {
  const { role } = useAuth()
  if (role === 'STUDENT' || role === 'PARENT') return <StudentAnnouncements />
  return <AnnouncementsPage />
}
