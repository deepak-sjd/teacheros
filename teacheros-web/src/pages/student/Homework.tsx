import { useEffect, useState, type FormEvent } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { Button, EmptyState, ErrorBanner, Skeleton, Textarea } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { formatDate, formatDateTime } from '@/lib/format'
import type { DownloadUrlResponse, Homework, HomeworkSubmission, Page, Student } from '@/lib/types'

/**
 * Submission area under one homework card. A STUDENT can submit/resubmit
 * (until the server says submissions are closed); a PARENT sees the same
 * data for their child, read-only - the backend only lets a PARENT read
 * (and rejects submit with a 403 anyway), so there is no form for them.
 */
function SubmissionPanel({
  homework,
  student,
  canSubmit,
}: {
  homework: Homework
  student: Student
  canSubmit: boolean
}) {
  const [submission, setSubmission] = useState<HomeworkSubmission | null>(null)
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setSubmission(null)
    setEditing(false)
    setError(null)
    api
      .get<HomeworkSubmission>(`/api/v1/homework/${homework.id}/students/${student.id}/submission`)
      .then(setSubmission)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the submission.'))
  }, [homework.id, student.id])

  async function handleOpenFile() {
    if (!submission?.id) return
    try {
      const { url } = await api.get<DownloadUrlResponse>(`/api/v1/homework/submissions/${submission.id}/file-url`)
      window.open(url, '_blank')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open the file.')
    }
  }

  function startEditing() {
    setText(submission?.text ?? '')
    setFile(null)
    setError(null)
    setEditing(true)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!text.trim() && !file && !submission?.originalFilename) {
      setError('Write an answer or attach a file before submitting.')
      return
    }

    const formData = new FormData()
    if (text.trim()) formData.append('text', text)
    if (file) formData.append('file', file)

    setSaving(true)
    try {
      const res = await api.uploadFile<HomeworkSubmission>(`/api/v1/homework/${homework.id}/submission`, formData)
      setSubmission(res)
      setEditing(false)
      setFile(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit your homework.')
    } finally {
      setSaving(false)
    }
  }

  if (submission === null) {
    return error ? <ErrorBanner message={error} /> : <Skeleton className="h-10 mt-3" />
  }

  const notSubmitted = submission.status === 'NOT_SUBMITTED'
  const showForm = canSubmit && submission.submissionsOpen && (notSubmitted || editing)

  return (
    <div className="mt-3 pt-3 border-t border-line">
      {error && <ErrorBanner message={error} />}

      {notSubmitted && !showForm && (
        <p className="text-sm text-slate-light">
          {submission.submissionsOpen ? 'Not submitted yet.' : 'Not submitted \u2014 the due date has passed.'}
        </p>
      )}

      {!notSubmitted && !editing && (
        <div className="space-y-2">
          <p className="text-xs text-slate-light">
            {submission.status === 'REVIEWED' ? 'Reviewed' : 'Submitted'}
            {submission.submittedAt ? ` \u00b7 ${formatDateTime(submission.submittedAt)}` : ''}
          </p>
          {submission.text && <p className="text-sm text-ink whitespace-pre-wrap">{submission.text}</p>}
          {submission.originalFilename && (
            <button
              onClick={handleOpenFile}
              className="text-sm text-slate hover:text-ink underline underline-offset-2"
            >
              {submission.originalFilename}
            </button>
          )}

          {submission.status === 'REVIEWED' && (
            <div className="rounded-md bg-chalk-soft px-3 py-2">
              {submission.grade && <p className="text-sm font-medium text-ink">Grade: {submission.grade}</p>}
              {submission.teacherFeedback && (
                <p className="text-sm text-ink whitespace-pre-wrap">{submission.teacherFeedback}</p>
              )}
            </div>
          )}

          {canSubmit && submission.submissionsOpen && (
            <Button variant="secondary" onClick={startEditing}>
              Resubmit
            </Button>
          )}
        </div>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} className="space-y-3">
          <Textarea
            label="Your answer"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={10000}
            rows={4}
          />
          <label className="block">
            <span className="block text-sm font-medium text-ink mb-1.5">
              Attach a file {submission.originalFilename ? '(replaces your current file)' : '(optional)'}
            </span>
            <input
              type="file"
              accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.webp,.txt"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-slate file:mr-3 file:rounded-md file:border-0 file:bg-paper-dim file:px-3 file:py-2 file:text-sm file:font-medium file:text-ink hover:file:bg-line"
            />
            <span className="block text-xs text-slate-light mt-1">PDF, Word, image, or text. Max 20MB.</span>
          </label>
          <div className="flex gap-2 justify-end">
            {editing && (
              <Button type="button" variant="secondary" onClick={() => setEditing(false)}>
                Cancel
              </Button>
            )}
            <Button type="submit" disabled={saving}>
              {saving ? 'Submitting…' : notSubmitted ? 'Submit' : 'Resubmit'}
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}

function HomeworkList({ student }: { student: Student }) {
  const { role } = useAuth()
  const [items, setItems] = useState<Homework[] | null>(null)

  useEffect(() => {
    if (!student.batchId) {
      setItems([])
      return
    }
    setItems(null)
    api
      .get<Page<Homework>>('/api/v1/homework', { batchId: student.batchId, size: 50 })
      .then((page) => setItems(page.content))
  }, [student.batchId])

  if (items === null) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 rounded-lg" />
        ))}
      </div>
    )
  }
  if (!student.batchId || items.length === 0) {
    return <EmptyState title="No homework assigned" description="Nothing due for this batch right now." />
  }

  const sorted = [...items].sort((a, b) => a.dueDate.localeCompare(b.dueDate))

  return (
    <div className="space-y-2">
      {sorted.map((h) => (
        <div key={h.id} className="border border-line rounded-lg bg-white p-4">
          <div className="flex items-start justify-between gap-4">
            <p className="font-medium text-ink">{h.title}</p>
            <span className="text-xs text-slate whitespace-nowrap">Due {formatDate(h.dueDate)}</span>
          </div>
          {h.description && <p className="text-sm text-slate mt-1">{h.description}</p>}
          <SubmissionPanel homework={h} student={student} canSubmit={role === 'STUDENT'} />
        </div>
      ))}
    </div>
  )
}

export function StudentHomework() {
  return (
    <StudentSectionShell title="Homework" description="Assignments and due dates for your batch.">
      {(student) => <HomeworkList student={student} />}
    </StudentSectionShell>
  )
}
