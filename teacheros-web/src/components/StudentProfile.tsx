import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type {
  AppNotification,
  AttendanceSummaryResponse,
  DownloadUrlResponse,
  Fee,
  Homework,
  Page,
  Student,
  StudentMarkHistoryEntry,
  StudentRank,
  StudyMaterial,
  TimetableSlot,
} from '@/lib/types'
import { Button, StatBlock, Ledger, Row, Cell, EmptyState, ErrorBanner } from '@/components/ui'
import { MessageThread } from '@/components/MessageThread'
import { FeeLedgerBreakdown } from '@/components/FeeLedgerBreakdown'
import { formatCurrency, formatDate } from '@/lib/format'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function StudentProfile({ studentId, showStudentHeader = true }: { studentId: string; showStudentHeader?: boolean }) {
  const { role } = useAuth()
  const [student, setStudent] = useState<Student | null>(null)
  const [summary, setSummary] = useState<AttendanceSummaryResponse | null>(null)
  const [fees, setFees] = useState<Fee[]>([])
  const [homework, setHomework] = useState<Homework[]>([])
  const [materials, setMaterials] = useState<StudyMaterial[]>([])
  const [slots, setSlots] = useState<TimetableSlot[]>([])
  const [marks, setMarks] = useState<StudentMarkHistoryEntry[]>([])
  const [rank, setRank] = useState<StudentRank | null>(null)
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [downloadingReportCard, setDownloadingReportCard] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    // Student record is always fetched now (not just when showing the
    // header) - homework/material/timetable are scoped by batchId, which
    // only the student record has, so we need it even on the student's own
    // read-only view.
    api
      .get<Student>(`/api/v1/students/${studentId}`)
      .then((s) => {
        setStudent(s)
        return Promise.all([
          api.get<AttendanceSummaryResponse>(`/api/v1/attendance/students/${studentId}/summary`),
          api.get<Fee[]>(`/api/v1/fees/students/${studentId}`),
          s.batchId
            ? api.get<Page<Homework>>('/api/v1/homework', { batchId: s.batchId, size: 50 })
            : Promise.resolve<Page<Homework>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 }),
          s.batchId
            ? api.get<Page<StudyMaterial>>('/api/v1/study-material', { batchId: s.batchId, size: 50 })
            : Promise.resolve<Page<StudyMaterial>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 }),
          s.batchId
            ? api.get<TimetableSlot[]>(`/api/v1/timetable/batches/${s.batchId}`)
            : Promise.resolve<TimetableSlot[]>([]),
          api.get<AppNotification[]>(`/api/v1/notifications/students/${studentId}`),
          api.get<StudentMarkHistoryEntry[]>(`/api/v1/tests/students/${studentId}/marks`),
          api.get<StudentRank>(`/api/v1/tests/students/${studentId}/rank`),
        ])
      })
      .then(([summ, f, hw, mat, tt, notifs, mk, rk]) => {
        setSummary(summ)
        setFees(f)
        setHomework(hw.content)
        setMaterials(mat.content)
        setSlots(tt)
        setNotifications(notifs)
        setMarks(mk)
        setRank(rk)
      })
      .catch(() => setError('Could not find that student. Double-check the ID and try again.'))
      .finally(() => setLoading(false))
  }, [studentId])

  async function handleMarkNotificationRead(n: AppNotification) {
    if (n.read) return
    await api.patch(`/api/v1/notifications/${n.id}/read`)
    setNotifications((prev) => prev.map((it) => (it.id === n.id ? { ...it, read: true } : it)))
  }

  async function handleDownload(id: string) {
    const { url } = await api.get<DownloadUrlResponse>(`/api/v1/study-material/${id}/download-url`)
    window.open(url, '_blank')
  }

  async function handleDownloadReportCard() {
    if (!student) return
    setDownloadingReportCard(true)
    setDownloadError(null)
    try {
      await api.downloadFile(
        `/api/v1/reports/students/${studentId}/report-card.pdf`,
        `${student.fullName.replace(/\s+/g, '-')}-report-card.pdf`,
      )
    } catch (err) {
      setDownloadError(err instanceof ApiError ? err.message : 'Could not download the report card.')
    } finally {
      setDownloadingReportCard(false)
    }
  }

  if (loading) return <p className="text-slate text-sm">Loading…</p>
  if (error) return <EmptyState title="Not found" description={error} />

  const totalDue = fees.reduce((sum, f) => sum + (f.amountDue - f.amountPaid), 0)

  return (
    <div>
      {showStudentHeader && student && (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-6">
          <div>
            <h2 className="font-display text-2xl text-ink">{student.fullName}</h2>
            <p className="text-slate text-sm mt-0.5">
              {student.grade ? `Grade ${student.grade} \u00b7 ` : ''}Guardian: {student.guardianPhone}
            </p>
          </div>
          <Button variant="secondary" onClick={handleDownloadReportCard} disabled={downloadingReportCard} className="shrink-0">
            {downloadingReportCard ? 'Preparing…' : 'Download report card (PDF)'}
          </Button>
        </div>
      )}

      {downloadError && <ErrorBanner message={downloadError} />}

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        <StatBlock
          label="Attendance"
          value={summary ? `${summary.attendancePercentage.toFixed(0)}%` : '\u2014'}
          sub={summary ? `${summary.sessionsAttended} of ${summary.sessionsRecorded} sessions` : undefined}
        />
        <StatBlock
          label="Class rank"
          value={rank?.rank ? `#${rank.rank}` : '\u2014'}
          sub={rank?.rank ? `of ${rank.rankedStudentCount} ranked` : 'Not enough graded tests yet'}
        />
        <StatBlock label="Outstanding balance" value={formatCurrency(totalDue)} />
        <StatBlock label="Fee records" value={String(fees.length)} />
      </div>

      <h3 className="font-display text-xl text-ink mb-3">Test results</h3>
      {marks.length === 0 ? (
        <EmptyState title="No test results yet" description="Nothing graded for this student yet." />
      ) : (
        <Ledger columns={['Test', 'Subject', 'Date', 'Score']}>
          {marks.map((m) => (
            <Row key={m.testId}>
              <Cell>{m.testTitle}</Cell>
              <Cell>{m.subject ?? '\u2014'}</Cell>
              <Cell>{formatDate(m.testDate)}</Cell>
              <Cell className="font-medium">
                {m.marksObtained === null ? 'Not graded' : `${m.marksObtained} / ${m.maxMarks}`}
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}

      <h3 className="font-display text-xl text-ink mb-3 mt-8">Alerts</h3>
      {notifications.length === 0 ? (
        <EmptyState title="No alerts" description="Attendance and fee alerts from your teacher will show up here." />
      ) : (
        <div className="space-y-2 mb-8">
          {notifications.map((n) => (
            <button
              key={n.id}
              onClick={() => handleMarkNotificationRead(n)}
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
      )}

      <h3 className="font-display text-xl text-ink mb-3 mt-8">Messages</h3>
      <div className="mb-8">
        <MessageThread studentId={studentId} />
      </div>

      <h3 className="font-display text-xl text-ink mb-3">Timetable</h3>
      {slots.length === 0 ? (
        <EmptyState title="No schedule set" description="Your teacher hasn't set a weekly schedule for this batch yet." />
      ) : (
        <div className="grid grid-cols-7 gap-3 mb-8">
          {DAYS.map((day, i) => {
            const dayOfWeek = i + 1
            const daySlots = slots
              .filter((s) => s.dayOfWeek === dayOfWeek)
              .sort((a, b) => a.startTime.localeCompare(b.startTime))
            return (
              <div key={day}>
                <div className="text-sm font-medium text-slate mb-2">{day}</div>
                <div className="space-y-2">
                  {daySlots.length === 0 ? (
                    <div className="text-xs text-slate-light">—</div>
                  ) : (
                    daySlots.map((slot) => (
                      <div key={slot.id} className="rounded-md border border-line bg-white p-2 text-xs">
                        <div className="font-medium text-ink">
                          {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                        </div>
                        {slot.subject && <div className="text-slate">{slot.subject}</div>}
                      </div>
                    ))
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      <h3 className="font-display text-xl text-ink mb-3">Homework</h3>
      {homework.length === 0 ? (
        <EmptyState title="No homework assigned" description="Nothing due for this batch right now." />
      ) : (
        <div className="space-y-2 mb-8">
          {homework.map((h) => (
            <div key={h.id} className="border border-line rounded-lg bg-white p-4">
              <div className="flex items-start justify-between gap-4">
                <p className="font-medium text-ink">{h.title}</p>
                <span className="text-xs text-slate whitespace-nowrap">Due {formatDate(h.dueDate)}</span>
              </div>
              {h.description && <p className="text-sm text-slate mt-1">{h.description}</p>}
            </div>
          ))}
        </div>
      )}

      <h3 className="font-display text-xl text-ink mb-3">Study material</h3>
      {materials.length === 0 ? (
        <EmptyState title="No files yet" description="Nothing uploaded for this batch right now." />
      ) : (
        <div className="space-y-2 mb-8">
          {materials.map((m) => (
            <div key={m.id} className="border border-line rounded-lg bg-white p-4">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-medium text-ink">{m.title}</p>
                  {m.description && <p className="text-sm text-slate mt-1">{m.description}</p>}
                  <p className="text-xs text-slate mt-1">
                    {m.originalFilename} {'\u00b7'} {formatFileSize(m.fileSizeBytes)}
                  </p>
                </div>
                <button
                  onClick={() => handleDownload(m.id)}
                  className="text-sm text-slate hover:text-ink underline underline-offset-2 whitespace-nowrap"
                >
                  Download
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <h3 className="font-display text-xl text-ink mb-3">Fee ledger</h3>
      <FeeLedgerBreakdown studentId={studentId} canWaive={role === 'OWNER' || role === 'ADMIN' || role === 'ACCOUNTANT'} />
    </div>
  )
}
