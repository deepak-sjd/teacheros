import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState, Skeleton } from '@/components/ui'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { AppNotification, Student } from '@/lib/types'

function AlertsList({ student }: { student: Student }) {
  const [notifications, setNotifications] = useState<AppNotification[] | null>(null)

  useEffect(() => {
    setNotifications(null)
    api.get<AppNotification[]>(`/api/v1/notifications/students/${student.id}`).then(setNotifications)
  }, [student.id])

  async function handleMarkRead(n: AppNotification) {
    if (n.read) return
    await api.patch(`/api/v1/notifications/${n.id}/read`)
    setNotifications((prev) => (prev ? prev.map((it) => (it.id === n.id ? { ...it, read: true } : it)) : prev))
  }

  if (notifications === null) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 rounded-lg" />
        ))}
      </div>
    )
  }
  if (notifications.length === 0) {
    return <EmptyState title="No alerts" description="Attendance and fee alerts will show up here." />
  }

  return (
    <div className="space-y-2">
      {notifications.map((n) => (
        <button
          key={n.id}
          onClick={() => handleMarkRead(n)}
          className={`block w-full text-left rounded-lg border border-line p-4 transition-colors hover:bg-paper-dim/60 ${
            n.read ? 'bg-white' : 'bg-chalk/40'
          }`}
        >
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-start gap-2">
              {!n.read && <span className="h-2 w-2 rounded-full bg-danger shrink-0 mt-1.5" />}
              <div>
                <p className="font-medium text-ink text-sm">{n.title}</p>
                <p className="text-sm text-slate mt-0.5">{n.message}</p>
              </div>
            </div>
            <span className="text-xs text-slate-light whitespace-nowrap">{formatDate(n.createdAt)}</span>
          </div>
        </button>
      ))}
    </div>
  )
}

export function StudentAlerts() {
  return (
    <StudentSectionShell title="Alerts" description="Attendance and fee notifications.">
      {(student) => <AlertsList student={student} />}
    </StudentSectionShell>
  )
}
