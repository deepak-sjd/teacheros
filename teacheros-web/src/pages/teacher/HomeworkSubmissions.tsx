import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { DownloadUrlResponse, Homework, HomeworkSubmission } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import {
  Button,
  Cell,
  EmptyState,
  ErrorBanner,
  Input,
  Ledger,
  LedgerSkeleton,
  Modal,
  Row,
  StatusPill,
  Textarea,
} from '@/components/ui'
import { formatDate, formatDateTime } from '@/lib/format'

/**
 * Teacher review screen for one homework: every student in the batch with
 * their submission status, and a modal to read an answer and leave
 * feedback/grade. The backend merges the roster, so students who haven't
 * submitted show up as NOT_SUBMITTED rather than being missing.
 */
export function HomeworkSubmissionsScreen({ homework, onBack }: { homework: Homework; onBack: () => void }) {
  const [rows, setRows] = useState<HomeworkSubmission[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reviewing, setReviewing] = useState<HomeworkSubmission | null>(null)

  function load() {
    setError(null)
    api
      .get<HomeworkSubmission[]>(`/api/v1/homework/${homework.id}/submissions`)
      .then(setRows)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load submissions.'))
  }

  useEffect(load, [homework.id])

  const submittedCount = rows ? rows.filter((r) => r.status !== 'NOT_SUBMITTED').length : 0

  return (
    <Layout>
      <PageHeader
        title={`Submissions: ${homework.title}`}
        description={
          rows
            ? `${submittedCount} of ${rows.length} submitted \u00b7 Due ${formatDate(homework.dueDate)}`
            : `Due ${formatDate(homework.dueDate)}`
        }
        action={
          <Button variant="secondary" onClick={onBack}>
            Back to homework
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      {rows === null ? (
        !error && <LedgerSkeleton columns={5} />
      ) : rows.length === 0 ? (
        <EmptyState title="No students in this batch" description="Add students to this batch first." />
      ) : (
        <Ledger columns={['Student', 'Status', 'Submitted', 'Grade', '']}>
          {rows.map((r) => (
            <Row key={r.studentId}>
              <Cell className="font-medium">{r.studentName}</Cell>
              <Cell>
                <StatusPill status={r.status} />
              </Cell>
              <Cell>{r.submittedAt ? formatDateTime(r.submittedAt) : '\u2014'}</Cell>
              <Cell>{r.grade ?? '\u2014'}</Cell>
              <Cell>
                {r.status !== 'NOT_SUBMITTED' && (
                  <button
                    onClick={() => setReviewing(r)}
                    className="text-sm text-slate hover:text-ink underline underline-offset-2"
                  >
                    {r.status === 'REVIEWED' ? 'Edit review' : 'Review'}
                  </button>
                )}
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}

      <ReviewModal
        submission={reviewing}
        onClose={() => setReviewing(null)}
        onSaved={() => {
          setReviewing(null)
          load()
        }}
      />
    </Layout>
  )
}

function ReviewModal({
  submission,
  onClose,
  onSaved,
}: {
  submission: HomeworkSubmission | null
  onClose: () => void
  onSaved: () => void
}) {
  const [feedback, setFeedback] = useState('')
  const [grade, setGrade] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (submission) {
      setFeedback(submission.teacherFeedback ?? '')
      setGrade(submission.grade ?? '')
      setError(null)
    }
  }, [submission])

  if (!submission) return null

  async function handleOpenFile() {
    if (!submission?.id) return
    try {
      const { url } = await api.get<DownloadUrlResponse>(`/api/v1/homework/submissions/${submission.id}/file-url`)
      window.open(url, '_blank')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open the file.')
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!submission?.id) return
    setError(null)
    if (!feedback.trim() && !grade.trim()) {
      setError('Add feedback or a grade before saving.')
      return
    }
    setSaving(true)
    try {
      await api.patch(`/api/v1/homework/submissions/${submission.id}/review`, {
        feedback: feedback.trim() || undefined,
        grade: grade.trim() || undefined,
      })
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the review.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={!!submission} onClose={onClose} title={`Review: ${submission.studentName}`}>
      {error && <ErrorBanner message={error} />}

      <div className="mb-4 rounded-md border border-line bg-paper-dim p-3 max-h-48 overflow-y-auto">
        {submission.text ? (
          <p className="text-sm text-ink whitespace-pre-wrap">{submission.text}</p>
        ) : (
          <p className="text-sm text-slate-light">No written answer.</p>
        )}
        {submission.originalFilename && (
          <button
            onClick={handleOpenFile}
            className="mt-2 text-sm text-slate hover:text-ink underline underline-offset-2"
          >
            {submission.originalFilename}
          </button>
        )}
        {submission.submittedAt && (
          <p className="text-xs text-slate-light mt-2">Submitted {formatDateTime(submission.submittedAt)}</p>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <Textarea label="Feedback" value={feedback} onChange={(e) => setFeedback(e.target.value)} maxLength={2000} />
        <Input
          label="Grade (optional)"
          placeholder="e.g. A or 8/10"
          value={grade}
          onChange={(e) => setGrade(e.target.value)}
          maxLength={50}
        />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save review'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
