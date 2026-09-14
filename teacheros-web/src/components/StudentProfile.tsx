import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import type { AttendanceSummaryResponse, Fee, Homework, Page, Student } from '@/lib/types'
import { StatBlock, Ledger, Row, Cell, StatusPill, EmptyState } from '@/components/ui'
import { formatCurrency, formatDate } from '@/lib/format'

export function StudentProfile({ studentId, showStudentHeader = true }: { studentId: string; showStudentHeader?: boolean }) {
  const [student, setStudent] = useState<Student | null>(null)
  const [summary, setSummary] = useState<AttendanceSummaryResponse | null>(null)
  const [fees, setFees] = useState<Fee[]>([])
  const [homework, setHomework] = useState<Homework[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    // Student record is always fetched now (not just when showing the
    // header) - homework is scoped by batchId, which only the student
    // record has, so we need it even on the student's own read-only view.
    api
      .get<Student>(`/api/v1/students/${studentId}`)
      .then((s) => {
        setStudent(s)
        return Promise.all([
          api.get<AttendanceSummaryResponse>(`/api/v1/attendance/students/${studentId}/summary`),
          api.get<Fee[]>(`/api/v1/fees/students/${studentId}`),
          s.batchId
            ? api.get<Page<Homework>>('/api/v1/homework', { batchId: s.batchId, size: 50 })
        : Promise.resolve<Page<Homework>>({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 0 }),        ])
      })
      .then(([summ, f, hw]) => {
        setSummary(summ)
        setFees(f)
        setHomework(hw.content)
      })
      .catch(() => setError('Could not find that student. Double-check the ID and try again.'))
      .finally(() => setLoading(false))
  }, [studentId])

  if (loading) return <p className="text-slate text-sm">Loading…</p>
  if (error) return <EmptyState title="Not found" description={error} />

  const totalDue = fees.reduce((sum, f) => sum + (f.amountDue - f.amountPaid), 0)

  return (
    <div>
      {showStudentHeader && student && (
        <div className="mb-6">
          <h2 className="font-display text-2xl text-ink">{student.fullName}</h2>
          <p className="text-slate text-sm mt-0.5">
            {student.grade ? `Grade ${student.grade} \u00b7 ` : ''}Guardian: {student.guardianPhone}
          </p>
        </div>
      )}

      <div className="grid grid-cols-3 gap-4 mb-8">
        <StatBlock
          label="Attendance"
          value={summary ? `${summary.attendancePercentage.toFixed(0)}%` : '\u2014'}
          sub={summary ? `${summary.sessionsAttended} of ${summary.sessionsRecorded} sessions` : undefined}
        />
        <StatBlock label="Outstanding balance" value={formatCurrency(totalDue)} />
        <StatBlock label="Fee records" value={String(fees.length)} />
      </div>

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

      <h3 className="font-display text-xl text-ink mb-3">Fee ledger</h3>
      {fees.length === 0 ? (
        <EmptyState title="No fees on record" />
      ) : (
        <Ledger columns={['Due date', 'Amount due', 'Amount paid', 'Balance', 'Status']}>
          {fees.map((f) => (
            <Row key={f.id}>
              <Cell>{formatDate(f.dueDate)}</Cell>
              <Cell>{formatCurrency(f.amountDue)}</Cell>
              <Cell>{formatCurrency(f.amountPaid)}</Cell>
              <Cell className="font-medium">{formatCurrency(f.amountDue - f.amountPaid)}</Cell>
              <Cell>
                <StatusPill status={f.status} />
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}
    </div>
  )
}
