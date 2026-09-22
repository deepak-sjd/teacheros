import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState, Ledger, Row, Cell } from '@/components/ui'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { Student, StudentMarkHistoryEntry } from '@/lib/types'

function MarksList({ student }: { student: Student }) {
  const [marks, setMarks] = useState<StudentMarkHistoryEntry[] | null>(null)

  useEffect(() => {
    setMarks(null)
    api.get<StudentMarkHistoryEntry[]>(`/api/v1/tests/students/${student.id}/marks`).then(setMarks)
  }, [student.id])

  if (marks === null) return <p className="text-slate text-sm">Loading…</p>
  if (marks.length === 0) return <EmptyState title="No test results yet" description="Nothing graded yet." />

  const sorted = [...marks].sort((a, b) => b.testDate.localeCompare(a.testDate))
  const graded = sorted.filter((m) => m.marksObtained !== null)
  const avgPercent =
    graded.length > 0
      ? (graded.reduce((sum, m) => sum + (m.marksObtained! / m.maxMarks) * 100, 0) / graded.length).toFixed(0)
      : null

  return (
    <div>
      {avgPercent !== null && (
        <p className="text-sm text-slate mb-4">
          Average score across {graded.length} graded test{graded.length === 1 ? '' : 's'}:{' '}
          <span className="font-medium text-ink">{avgPercent}%</span>
        </p>
      )}
      <Ledger columns={['Test', 'Subject', 'Date', 'Score']}>
        {sorted.map((m) => (
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
    </div>
  )
}

export function StudentTests() {
  return (
    <StudentSectionShell title="Tests & marks" description="Test results and scores.">
      {(student) => <MarksList student={student} />}
    </StudentSectionShell>
  )
}
