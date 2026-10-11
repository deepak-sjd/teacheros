import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { Batch, ExamTerm, Page, ReportCardRemark, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Select, Skeleton, Textarea } from '@/components/ui'

/**
 * Whole class, one submission - same workflow as GradingScreen in
 * teacher/Tests.tsx. A blank box clears that student's remark.
 */
export function ReportRemarksPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [terms, setTerms] = useState<ExamTerm[]>([])
  const [batchId, setBatchId] = useState('')
  const [termId, setTermId] = useState('')
  const [students, setStudents] = useState<Student[]>([])
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [setupLoading, setSetupLoading] = useState(true)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      api.get<Page<Batch>>('/api/v1/batches', { size: 100 }),
      api.get<ExamTerm[]>('/api/v1/exam-terms'),
    ])
      .then(([b, t]) => {
        setBatches(b.content)
        setTerms(t)
        if (b.content.length > 0) setBatchId(b.content[0].id)
        if (t.length > 0) setTermId(t[t.length - 1].id) // newest term first guess
      })
      .finally(() => setSetupLoading(false))
  }, [])

  useEffect(() => {
    if (!batchId || !termId) return
    setLoading(true)
    setSaved(false)
    setError(null)
    Promise.all([
      api.get<Page<Student>>('/api/v1/students', { batchId, size: 200 }),
      api.get<ReportCardRemark[]>(`/api/v1/report-cards/terms/${termId}/remarks`, { batchId }),
    ])
      .then(([s, r]) => {
        setStudents(s.content)
        const initial: Record<string, string> = {}
        for (const item of r) initial[item.studentId] = item.remark
        setDrafts(initial)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load remarks.'))
      .finally(() => setLoading(false))
  }, [batchId, termId])

  async function handleSave() {
    setError(null)
    setSaved(false)
    setSaving(true)
    try {
      await api.put(`/api/v1/report-cards/terms/${termId}/remarks`, {
        entries: students.map((s) => ({ studentId: s.id, remark: drafts[s.id] ?? '' })),
      })
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save remarks.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <PageHeader title="Report remarks" description="Write a short remark for each student, per term." />

      {setupLoading ? (
        <Skeleton className="h-16 rounded-lg" />
      ) : batches.length === 0 ? (
        <EmptyState title="No batches yet" description="Create a batch first." />
      ) : terms.length === 0 ? (
        <EmptyState
          title="No terms yet"
          description="Create an exam term before writing remarks."
          action={
            <Link to="/app/exam-terms">
              <Button variant="secondary">Go to terms</Button>
            </Link>
          }
        />
      ) : (
        <>
          <div className="flex flex-wrap gap-4 mb-6">
            <div className="w-64">
              <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
                {batches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </Select>
            </div>
            <div className="w-64">
              <Select label="Term" value={termId} onChange={(e) => setTermId(e.target.value)}>
                {terms.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {error && <ErrorBanner message={error} />}
          {saved && (
            <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
              Remarks saved.
            </div>
          )}

          {loading ? (
            <div className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-20 rounded-lg" />
              ))}
            </div>
          ) : students.length === 0 ? (
            <EmptyState title="No students in this batch" description="Add students to this batch first." />
          ) : (
            <>
              <div className="space-y-3 mb-6">
                {students.map((s) => (
                  <div key={s.id} className="border border-line rounded-lg bg-white p-4">
                    <p className="text-sm font-medium text-ink mb-2">{s.fullName}</p>
                    <Textarea
                      rows={2}
                      maxLength={1000}
                      placeholder="Remark (optional)"
                      value={drafts[s.id] ?? ''}
                      onChange={(e) => {
                        setDrafts((prev) => ({ ...prev, [s.id]: e.target.value }))
                        setSaved(false)
                      }}
                    />
                  </div>
                ))}
              </div>
              <Button onClick={handleSave} disabled={saving}>
                {saving ? 'Saving…' : 'Save remarks'}
              </Button>
            </>
          )}
        </>
      )}
    </Layout>
  )
}
