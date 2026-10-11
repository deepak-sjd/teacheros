import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { StatBlock, StatBlockSkeleton, EmptyState, Skeleton } from '@/components/ui'
import { StudyPlanCard } from '@/components/StudyPlanCard'
import { api } from '@/lib/api'
import { BarChart } from '@/components/Charts'
import { JoinClassLink } from '@/components/JoinClassLink'
import { formatCurrency, formatDate, isSlotLiveNow, MEETING_PLATFORM_LABELS, todayIso } from '@/lib/format'
import type {
  AppNotification,
  AttendanceSummaryResponse,
  Fee,
  Homework,
  Page,
  Student,
  StudentMarkHistoryEntry,
  StudentRank,
  StudyPlanResponse,
  TimetableSlot,
} from '@/lib/types'

const DAY_NAMES = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

// ISO's Date.getDay() is 0=Sun..6=Sat; TimetableSlot.dayOfWeek is 1=Mon..7=Sun
// (see TimetableSlot's own comment in types/timetable.ts) - this converts one to the
// other rather than duplicating that convention decision here.
function todayDayOfWeek(): number {
  const jsDay = new Date().getDay()
  return jsDay === 0 ? 7 : jsDay
}

function OverviewPanel({ student }: { student: Student }) {
  const [summary, setSummary] = useState<AttendanceSummaryResponse | null>(null)
  const [fees, setFees] = useState<Fee[]>([])
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [homework, setHomework] = useState<Homework[]>([])
  const [slots, setSlots] = useState<TimetableSlot[]>([])
  const [marks, setMarks] = useState<StudentMarkHistoryEntry[]>([])
  const [rank, setRank] = useState<StudentRank | null>(null)
  const [studyPlan, setStudyPlan] = useState<StudyPlanResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.get<AttendanceSummaryResponse>(`/api/v1/attendance/students/${student.id}/summary`),
      api.get<Fee[]>(`/api/v1/fees/students/${student.id}`),
      api.get<AppNotification[]>(`/api/v1/notifications/students/${student.id}`),
      // Homework/timetable are scoped to a batch, not a student - a student
      // with no batch assigned yet just sees empty sections rather than a
      // failed request.
      student.batchId
        ? api.get<Page<Homework>>('/api/v1/homework', { batchId: student.batchId, size: 50 })
        : Promise.resolve<Page<Homework>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 }),
      student.batchId
        ? api.get<TimetableSlot[]>(`/api/v1/timetable/batches/${student.batchId}`)
        : Promise.resolve<TimetableSlot[]>([]),
      api.get<StudentMarkHistoryEntry[]>(`/api/v1/tests/students/${student.id}/marks`),
      api.get<StudentRank>(`/api/v1/tests/students/${student.id}/rank`),
      // Never calls the AI provider - StudyPlanService#get just recomputes
      // the weak-subject breakdown from existing marks (see its javadoc).
      // Generating/regenerating the plan text itself is an explicit click
      // inside StudyPlanCard, not something that happens on page load.
      api.get<StudyPlanResponse>(`/api/v1/study-plan/students/${student.id}`),
    ])
      .then(([summ, f, notifs, hw, tt, m, r, sp]) => {
        setSummary(summ)
        setFees(f)
        setNotifications(notifs)
        setHomework(hw.content)
        setSlots(tt)
        setMarks(m)
        setRank(r)
        setStudyPlan(sp)
      })
      .finally(() => setLoading(false))
  }, [student.id, student.batchId])

  if (loading) {
    return (
      <div>
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-8">
          <StatBlockSkeleton />
          <StatBlockSkeleton />
          <StatBlockSkeleton />
          <StatBlockSkeleton />
        </div>
        <Skeleton className="h-5 w-32 mb-3" />
        <div className="space-y-2 mb-8">
          <Skeleton className="h-16 rounded-lg" />
          <Skeleton className="h-16 rounded-lg" />
        </div>
        <Skeleton className="h-5 w-40 mb-3" />
        <div className="space-y-2">
          <Skeleton className="h-16 rounded-lg" />
          <Skeleton className="h-16 rounded-lg" />
        </div>
      </div>
    )
  }

  const totalDue = fees.reduce((sum, f) => sum + (f.amountDue - f.amountPaid), 0)
  const unread = notifications.filter((n) => !n.read)

  const today = todayIso()
  const upcomingHomework = homework
    .filter((h) => h.dueDate >= today)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
    .slice(0, 3)

  const todaySlots = slots
    .filter((s) => s.dayOfWeek === todayDayOfWeek())
    .sort((a, b) => a.startTime.localeCompare(b.startTime))

  const recentMarks = marks.slice(0, 3)

  // Average score per subject, from the same mark history already fetched
  // above - no separate API call needed for this chart.
  const gradedMarks = marks.filter((m) => m.marksObtained !== null)
  const percentagesBySubject = new Map<string, number[]>()
  for (const m of gradedMarks) {
    const subject = m.subject ?? 'General'
    const pct = (m.marksObtained! / m.maxMarks) * 100
    percentagesBySubject.set(subject, [...(percentagesBySubject.get(subject) ?? []), pct])
  }
  const subjectPerformanceData = [...percentagesBySubject.entries()]
    .map(([subject, values]) => ({
      label: subject,
      value: values.reduce((sum, v) => sum + v, 0) / values.length,
    }))
    .sort((a, b) => a.label.localeCompare(b.label))

  return (
    <div>
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-8">
        <StatBlock
          label="Attendance"
          value={summary ? `${summary.attendancePercentage.toFixed(0)}%` : '\u2014'}
          sub={summary ? `${summary.sessionsAttended} of ${summary.sessionsRecorded} sessions` : undefined}
        />
        <StatBlock label="Outstanding balance" value={formatCurrency(totalDue)} />
        <StatBlock label="Unread alerts" value={String(unread.length)} />
        <StatBlock
          label="Batch rank"
          value={rank && rank.rank !== null ? `${rank.rank} of ${rank.rankedStudentCount}` : '\u2014'}
        />
      </div>

      {studyPlan && <StudyPlanCard studentId={student.id} initial={studyPlan} />}

      <h3 className="font-display text-xl text-ink mb-3">Today's classes</h3>
      {todaySlots.length === 0 ? (
        <EmptyState title="No classes today" description={`Nothing on the timetable for ${DAY_NAMES[todayDayOfWeek()]}.`} />
      ) : (
        <div className="space-y-2 mb-8">
          {todaySlots.map((slot) => {
            const live = slot.meetingLink ? isSlotLiveNow(slot.dayOfWeek, slot.startTime, slot.endTime) : false
            return (
              <div key={slot.id} className="flex items-center justify-between border border-line rounded-lg bg-white p-4">
                <div>
                  <p className="font-medium text-ink text-sm">{slot.subject ?? 'Class'}</p>
                  <p className="text-xs text-slate-light mt-0.5">
                    {slot.startTime.slice(0, 5)} – {slot.endTime.slice(0, 5)}
                  </p>
                </div>
                {slot.meetingLink && (
                  <JoinClassLink
                    slot={slot}
                    className={`shrink-0 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                      live
                        ? 'bg-ink text-paper hover:bg-ink-light'
                        : 'bg-white text-ink border border-line hover:bg-paper-dim'
                    }`}
                  >
                    {live ? 'Join now' : `${MEETING_PLATFORM_LABELS[slot.meetingPlatform ?? 'OTHER']} link`}
                  </JoinClassLink>
                )}
              </div>
            )
          })}
        </div>
      )}

      <h3 className="font-display text-xl text-ink mb-3">Upcoming homework</h3>
      {upcomingHomework.length === 0 ? (
        <EmptyState title="Nothing due" description="No upcoming homework for this batch." />
      ) : (
        <div className="space-y-2 mb-8">
          {upcomingHomework.map((h) => (
            <div key={h.id} className="flex items-center justify-between border border-line rounded-lg bg-white p-4">
              <p className="font-medium text-ink text-sm">{h.title}</p>
              <span className="text-xs text-slate-light whitespace-nowrap">Due {formatDate(h.dueDate)}</span>
            </div>
          ))}
        </div>
      )}

      <h3 className="font-display text-xl text-ink mb-3">Recent marks</h3>
      {recentMarks.length === 0 ? (
        <EmptyState title="No marks yet" description="Test results will show up here once a test is graded." />
      ) : (
        <div className="space-y-2 mb-8">
          {recentMarks.map((m) => (
            <div key={m.testId} className="flex items-center justify-between border border-line rounded-lg bg-white p-4">
              <div>
                <p className="font-medium text-ink text-sm">{m.testTitle}</p>
                <p className="text-xs text-slate-light mt-0.5">{m.subject ?? 'General'} · {formatDate(m.testDate)}</p>
              </div>
              <span className="text-sm font-medium text-ink whitespace-nowrap">
                {m.marksObtained ?? '\u2014'} / {m.maxMarks}
              </span>
            </div>
          ))}
        </div>
      )}

      {subjectPerformanceData.length > 0 && (
        <div className="border border-line rounded-lg bg-white p-5 mb-8">
          <h3 className="font-display text-lg text-ink mb-1">Performance by subject</h3>
          <p className="text-xs text-slate-light mb-4">Average score across all graded tests.</p>
          <BarChart
            data={subjectPerformanceData}
            maxValue={100}
            formatValue={(v) => `${Math.round(v)}%`}
            barColor="var(--color-chalk)"
          />
        </div>
      )}

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
    <StudentSectionShell title="Overview" description="Today's classes, homework, marks, fees and alerts at a glance.">
      {(student) => <OverviewPanel student={student} />}
    </StudentSectionShell>
  )
}
