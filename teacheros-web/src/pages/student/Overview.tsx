import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { StatBlock, EmptyState } from '@/components/ui'
import { api } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/format'
import type { AppNotification, AttendanceSummaryResponse, Fee, Student } from '@/lib/types'

function OverviewPanel({ student }: { student: Student }) {
  const [summary, setSummary] = useState<AttendanceSummaryResponse | null>(null)
  const [fees, setFees] = useState<Fee[]>([])
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.get<AttendanceSummaryResponse>(`/api/v1/attendance/students/${student.id}/summary`),
      api.get<Fee[]>(`/api/v1/fees/students/${student.id}`),
      api.get<AppNotification[]>(`/api/v1/notifications/students/${student.id}`),
    ])
      .then(([summ, f, notifs]) => {
        setSummary(summ)
        setFees(f)
        setNotifications(notifs)
      })
      .finally(() => setLoading(false))
  }, [student.id])

  if (loading) return <p className="text-slate text-sm">Loading…</p>

  const totalDue = fees.reduce((sum, f) => sum + (f.amountDue - f.amountPaid), 0)
  const unread = notifications.filter((n) => !n.read)

  return (
    <div>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <StatBlock
          label="Attendance"
          value={summary ? `${summary.attendancePercentage.toFixed(0)}%` : '\u2014'}
          sub={summary ? `${summary.sessionsAttended} of ${summary.sessionsRecorded} sessions` : undefined}
        />
        <StatBlock label="Outstanding balance" value={formatCurrency(totalDue)} />
        <StatBlock label="Unread alerts" value={String(unread.length)} />
      </div>

      <h3 className="font-display text-xl text-ink mb-3">Recent alerts</h3>
      {notifications.length === 0 ? (
        <EmptyState title="No alerts" description="Attendance and fee alerts will show up here." />
      ) : (
        <div className="space-y-2">
          {notifications.slice(0, 5).map((n) => (
            <div key={n.id} className="border border-line rounded-lg bg-white p-4">
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
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export function StudentOverview() {
  return (
    <StudentSectionShell title="Overview" description="Attendance, fees and recent alerts at a glance.">
      {(student) => <OverviewPanel student={student} />}
    </StudentSectionShell>
  )
}
