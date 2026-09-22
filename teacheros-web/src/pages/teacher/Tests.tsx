import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Assessment, Batch, MarkResponse, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select } from '@/components/ui'
import { formatDate, todayIso } from '@/lib/format'

export function TestsPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [tests, setTests] = useState<Assessment[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [grading, setGrading] = useState<Assessment | null>(null)

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => {
      setBatches(res.content)
      if (res.content.length > 0) setBatchId(res.content[0].id)
      else setLoading(false)
    })
  }, [])

  function load(forBatchId: string) {
    if (!forBatchId) return
    setLoading(true)
    api
      .get<Page<Assessment>>('/api/v1/tests', { batchId: forBatchId, size: 100 })
      .then((res) => setTests(res.content))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (batchId) load(batchId)
  }, [batchId])

  async function handleDelete(id: string) {
    if (!confirm('Delete this test and all its marks? This cannot be undone.')) return
    await api.del(`/api/v1/tests/${id}`)
    load(batchId)
  }

  if (grading) {
    return <GradingScreen test={grading} onBack={() => setGrading(null)} />
  }

  return (
    <Layout>
      <PageHeader
        title="Tests"
        description="Create a test for a batch, then grade every student in one sitting."
        action={batches.length > 0 ? <Button onClick={() => setCreateOpen(true)}>New test</Button> : undefined}
      />

      {batches.length === 0 ? (
        <EmptyState title="No batches yet" description="Create a batch first before adding a test." />
      ) : (
        <>
          <div className="mb-6 max-w-xs">
            <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
              {batches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </Select>
          </div>

          {loading ? (
            <p className="text-slate text-sm">Loading…</p>
          ) : tests.length === 0 ? (
            <EmptyState
              title="No tests for this batch"
              description="Create a test, then grade students once it's done."
              action={<Button onClick={() => setCreateOpen(true)}>New test</Button>}
            />
          ) : (
            <div className="space-y-2">
              {tests.map((t) => (
                <div key={t.id} className="border border-line rounded-lg bg-white p-4">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="font-medium text-ink">{t.title}</p>
                      <p className="text-xs text-slate mt-1">
                        {t.subject ? `${t.subject} \u00b7 ` : ''}
                        Out of {t.maxMarks} {'\u00b7'} {formatDate(t.testDate)}
                      </p>
                    </div>
                    <div className="flex items-center gap-3 whitespace-nowrap">
                      <button
                        onClick={() => setGrading(t)}
                        className="text-sm text-slate hover:text-ink underline underline-offset-2"
                      >
                        Grade
                      </button>
                      <button
                        onClick={() => handleDelete(t.id)}
                        className="text-sm text-danger hover:opacity-80 underline underline-offset-2"
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      <CreateTestModal
        open={createOpen}
        batchId={batchId}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          load(batchId)
        }}
      />
    </Layout>
  )
}

function CreateTestModal({
  open,
  batchId,
  onClose,
  onCreated,
}: {
  open: boolean
  batchId: string
  onClose: () => void
  onCreated: () => void
}) {
  const [title, setTitle] = useState('')
  const [subject, setSubject] = useState('')
  const [maxMarks, setMaxMarks] = useState('100')
  const [testDate, setTestDate] = useState(todayIso())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    const parsedMax = Number(maxMarks)
    if (!title.trim() || !parsedMax || parsedMax <= 0) {
      setError('Enter a title and a max marks value above 0.')
      return
    }

    setSaving(true)
    try {
      await api.post('/api/v1/tests', {
        batchId,
        title,
        subject: subject || undefined,
        maxMarks: parsedMax,
        testDate,
      })
      setTitle('')
      setSubject('')
      setMaxMarks('100')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this test.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New test">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <Input label="Subject (optional)" value={subject} onChange={(e) => setSubject(e.target.value)} />
        <Input
          label="Max marks"
          type="number"
          min="1"
          step="0.5"
          value={maxMarks}
          onChange={(e) => setMaxMarks(e.target.value)}
          required
        />
        <Input label="Test date" type="date" value={testDate} onChange={(e) => setTestDate(e.target.value)} required />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create test'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * Grade every student in the batch in one sitting, then submit once - the
 * same "whole class, one submission" workflow the mobile attendance screen
 * uses, not one API call per student.
 */
function GradingScreen({ test, onBack }: { test: Assessment; onBack: () => void }) {
  const [students, setStudents] = useState<Student[]>([])
  const [scores, setScores] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.get<Page<Student>>('/api/v1/students', { batchId: test.batchId, size: 200 }),
      api.get<MarkResponse[]>(`/api/v1/tests/${test.id}/marks`),
    ])
      .then(([studentsRes, marksRes]) => {
        setStudents(studentsRes.content)
        const initial: Record<string, string> = {}
        for (const m of marksRes) {
          if (m.marksObtained !== null) initial[m.studentId] = String(m.marksObtained)
        }
        setScores(initial)
      })
      .finally(() => setLoading(false))
  }, [test.id, test.batchId])

  async function handleSubmit() {
    setError(null)
    setSaving(true)
    try {
      const entries = students.map((s) => ({
        studentId: s.id,
        marksObtained: scores[s.id]?.trim() ? Number(scores[s.id]) : null,
      }))
      await api.post(`/api/v1/tests/${test.id}/marks`, { entries })
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save marks. Check for values above max marks.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <PageHeader
        title={`Grade: ${test.title}`}
        description={`Out of ${test.maxMarks} \u00b7 ${formatDate(test.testDate)}`}
        action={
          <Button variant="secondary" onClick={onBack}>
            Back to tests
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : students.length === 0 ? (
        <EmptyState title="No students in this batch" description="Add students to this batch first." />
      ) : saved ? (
        <EmptyState title="Marks saved" description={`Grades recorded for ${students.length} students.`} />
      ) : (
        <>
          <div className="space-y-2 mb-6">
            {students.map((s) => (
              <div key={s.id} className="flex items-center justify-between gap-4 border border-line rounded-lg bg-white p-3">
                <span className="text-sm text-ink">{s.fullName}</span>
                <input
                  type="number"
                  min="0"
                  max={test.maxMarks}
                  step="0.5"
                  placeholder="Not graded"
                  value={scores[s.id] ?? ''}
                  onChange={(e) => setScores((prev) => ({ ...prev, [s.id]: e.target.value }))}
                  className="w-28 rounded-md border border-line px-3 py-1.5 text-sm text-right focus:border-ink focus:ring-1 focus:ring-ink outline-none"
                />
              </div>
            ))}
          </div>
          <Button onClick={handleSubmit} disabled={saving}>
            {saving ? 'Saving…' : 'Save marks'}
          </Button>
        </>
      )}
    </Layout>
  )
}
