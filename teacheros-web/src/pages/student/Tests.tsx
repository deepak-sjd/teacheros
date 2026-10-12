import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { Button, EmptyState, ErrorBanner, Ledger, Row, Cell, StatBlock, StatBlockSkeleton, LedgerSkeleton } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { formatDate } from '@/lib/format'
import { TermResults } from '@/components/TermResults'
import type { Assessment, MyAttemptResponse, Page, Student, StudentMarkHistoryEntry, StudentRank } from '@/lib/types'

interface OnlineTestRow {
  test: Assessment
  status: MyAttemptResponse
}

/**
 * STUDENT only - the my-attempt/start/submit endpoints behind this are
 * hasRole('STUDENT') on the backend (there's no student-selection step;
 * the logged-in student's own identity resolves the attempt), so a
 * PARENT viewing a linked child's Tests & Marks page would just get a
 * 403 from every call here. Gated the same way CheckIn's nav entry is.
 */
function OnlineTestsSection({ student }: { student: Student }) {
  const navigate = useNavigate()
  const [rows, setRows] = useState<OnlineTestRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setRows(null)
    setError(null)
    if (!student.batchId) {
      setRows([])
      return
    }
    api
      .get<Page<Assessment>>('/api/v1/tests', { batchId: student.batchId, testType: 'ONLINE_MCQ', size: 100 })
      .then(async (res) => {
        const withStatus = await Promise.all(
          res.content.map(async (test) => ({
            test,
            status: await api.get<MyAttemptResponse>(`/api/v1/tests/${test.id}/my-attempt`),
          })),
        )
        setRows(withStatus)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load online tests.'))
  }, [student.id, student.batchId])

  if (error) return <ErrorBanner message={error} />
  if (rows === null) return <p className="text-slate text-sm">Loading…</p>
  if (rows.length === 0) return null // Nothing published - don't show an empty section at all.

  return (
    <div className="mb-8">
      <h3 className="font-display text-xl text-ink mb-3">Online tests</h3>
      <div className="space-y-2">
        {rows.map(({ test, status }) => (
          <div key={test.id} className="border border-line rounded-lg bg-white p-4 flex items-center justify-between gap-4">
            <div>
              <p className="font-medium text-ink">{test.title}</p>
              <p className="text-xs text-slate mt-1">
                {test.subject ? `${test.subject} \u00b7 ` : ''}
                {test.durationMinutes ? `${test.durationMinutes} min \u00b7 ` : ''}
                Out of {test.maxMarks}
              </p>
            </div>
            {status.status === 'SUBMITTED' ? (
              <span className="text-sm font-medium text-ink whitespace-nowrap">
                Scored {status.score} / {status.maxMarks}
              </span>
            ) : (
              <Button onClick={() => navigate(`/app/take-test?testId=${test.id}`)}>
                {status.status === 'IN_PROGRESS' ? 'Continue' : 'Start test'}
              </Button>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}

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

  if (marks === null) {
    return (
      <div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
          <StatBlockSkeleton />
          <StatBlockSkeleton />
          <StatBlockSkeleton />
        </div>
        <LedgerSkeleton columns={4} />
      </div>
    )
  }

  const sorted = [...marks].sort((a, b) => b.testDate.localeCompare(a.testDate))

  return (
    <div>
      {downloadError && <ErrorBanner message={downloadError} />}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-4">
        {rank && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 flex-1">
            <StatBlock label="Graded tests" value={String(rank.gradedTestCount)} />
            <StatBlock
              label="Average score"
              value={rank.rank !== null ? `${rank.averagePercentage}%` : '\u2014'}
            />
            <StatBlock
              label="Rank in batch"
              value={rank.rank !== null ? `${rank.rank} of ${rank.rankedStudentCount}` : `Not yet ranked`}
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
  const { role } = useAuth()
  return (
    <StudentSectionShell title="Tests & marks" description="Test results, scores, and your batch rank.">
      {(student) => (
        <>
          {role === 'STUDENT' && <OnlineTestsSection student={student} />}
          <TermResults studentId={student.id} />
          <MarksList student={student} />
        </>
      )}
    </StudentSectionShell>
  )
}
6