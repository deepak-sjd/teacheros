import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { Button, EmptyState, ErrorBanner, Ledger, Row, Cell, StatBlock } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { Student, StudentMarkHistoryEntry, StudentRank } from '@/lib/types'

function MarksList({ student }: { student: Student }) {
  const [marks, setMarks] = useState<StudentMarkHistoryEntry[] | null>(null)
  const [rank, setRank] = useState<StudentRank | null>(null)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  useEffect(() => {
    setMarks(null)
    setRank(null)
    setDownloadError(null)
    api.get<StudentMarkHistoryEntry[]>(`/api/v1/tests/students/${student.id}/marks`).then(setMarks)
    api.get<StudentRank>(`/api/v1/tests/students/${student.id}/rank`).then(setRank)
  }, [student.id])

  async function handleDownloadReportCard() {
    setDownloading(true)
    setDownloadError(null)
    try {
      await api.downloadFile(
        `/api/v1/reports/students/${student.id}/report-card.pdf`,
        `${student.fullName.replace(/\s+/g, '-')}-report-card.pdf`,
      )
    } catch (err) {
      setDownloadError(err instanceof ApiError ? err.message : 'Could not download the report card.')
    } finally {
      setDownloading(false)
    }
  }

  if (marks === null) return <p className="text-slate text-sm">Loading…</p>

  const sorted = [...marks].sort((a, b) => b.testDate.localeCompare(a.testDate))

  return (
    <div>
      {downloadError && <ErrorBanner message={downloadError} />}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-4">
        {rank && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 flex-1">
            <StatBlock
              label="Total marks"
              value={rank.rank !== null ? `${rank.totalMarksObtained} / ${rank.totalMaxMarks}` : '\u2014'}
            />
            <StatBlock label="Average score" value={rank.percentage !== null ? `${rank.percentage}%` : '\u2014'} />
            <StatBlock
              label="Rank in batch"
              value={rank.rank !== null ? `${rank.rank} of ${rank.totalStudents}` : `Not yet ranked`}
            />
          </div>
        )}
        <Button variant="secondary" onClick={handleDownloadReportCard} disabled={downloading} className="shrink-0">
          {downloading ? 'Preparing…' : 'Download report card (PDF)'}
        </Button>
      </div>

      {marks.length === 0 ? (
        <EmptyState title="No test results yet" description="Nothing graded yet." />
      ) : (
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
      )}
    </div>
  )
}

export function StudentTests() {
  return (
    <StudentSectionShell title="Tests & marks" description="Test results, scores, and your batch rank.">
      {(student) => <MarksList student={student} />}
    </StudentSectionShell>
  )
}
