import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { ExamTerm, GradeBand, GradeScaleResponse } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Modal, Row } from '@/components/ui'
import { formatDate } from '@/lib/format'

export function ExamTermsPage() {
  return (
    <Layout>
      <PageHeader
        title="Terms & grades"
        description="Define exam terms and the grade boundaries used on report cards."
      />
      <div className="space-y-12">
        <TermsSection />
        <GradeScaleSection />
      </div>
    </Layout>
  )
}

function TermsSection() {
  const [terms, setTerms] = useState<ExamTerm[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ExamTerm | null>(null)
  const [error, setError] = useState<string | null>(null)

  function load() {
    setLoading(true)
    api
      .get<ExamTerm[]>('/api/v1/exam-terms')
      .then(setTerms)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load terms.'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  async function handleDelete(t: ExamTerm) {
    if (!confirm(`Delete "${t.name}"? Teacher remarks for this term are removed too. Marks are not affected.`)) return
    setError(null)
    try {
      await api.del(`/api/v1/exam-terms/${t.id}`)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete this term.')
    }
  }

  return (
    <section>
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="font-display text-xl text-ink">Exam terms</h2>
          <p className="text-xs text-slate-light mt-1">
            A graded test counts toward a term when its test date falls inside the term's dates.
          </p>
        </div>
        <Button onClick={() => setModalOpen(true)}>New term</Button>
      </div>

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <LedgerSkeleton columns={4} />
      ) : terms.length === 0 ? (
        <EmptyState
          title="No terms yet"
          description="Create a term to show term-wise grades on report cards."
          action={<Button onClick={() => setModalOpen(true)}>New term</Button>}
        />
      ) : (
        <Ledger columns={['Term', 'Starts', 'Ends', '']}>
          {terms.map((t) => (
            <Row key={t.id}>
              <Cell className="font-medium">{t.name}</Cell>
              <Cell>{formatDate(t.startDate)}</Cell>
              <Cell>{formatDate(t.endDate)}</Cell>
              <Cell>
                <div className="flex gap-3 justify-end">
                  <button
                    onClick={() => setEditing(t)}
                    className="text-sm text-slate hover:text-ink underline underline-offset-2"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(t)}
                    className="text-sm text-danger hover:opacity-80 underline underline-offset-2"
                  >
                    Delete
                  </button>
                </div>
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}

      <TermModal
        open={modalOpen}
        term={null}
        onClose={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false)
          load()
        }}
      />
      <TermModal
        open={editing !== null}
        term={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null)
          load()
        }}
      />
    </section>
  )
}

function TermModal({
  open,
  term,
  onClose,
  onSaved,
}: {
  open: boolean
  term: ExamTerm | null
  onClose: () => void
  onSaved: () => void
}) {
  const isEdit = !!term
  const [name, setName] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // Same reused-modal reset as Students.tsx's StudentFormModal.
  useEffect(() => {
    if (!open) return
    setName(term?.name ?? '')
    setStartDate(term?.startDate ?? '')
    setEndDate(term?.endDate ?? '')
    setError(null)
  }, [open, term])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      const body = { name, startDate, endDate }
      if (term) {
        await api.patch(`/api/v1/exam-terms/${term.id}`, body)
      } else {
        await api.post('/api/v1/exam-terms', body)
      }
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save this term.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Edit term' : 'New term'}>
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Term 1" required />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Starts" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          <Input label="Ends" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
        </div>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : isEdit ? 'Save changes' : 'Create term'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

interface BandRow {
  grade: string
  min: string
  max: string
  remark: string
}

function toRows(bands: GradeBand[]): BandRow[] {
  return bands.map((b) => ({
    grade: b.grade,
    min: String(b.minPercentage),
    max: String(b.maxPercentage),
    remark: b.remark ?? '',
  }))
}

function GradeScaleSection() {
  const { role } = useAuth()
  const canEdit = role === 'OWNER' || role === 'ADMIN'

  const [scale, setScale] = useState<GradeScaleResponse | null>(null)
  const [rows, setRows] = useState<BandRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  function apply(res: GradeScaleResponse) {
    setScale(res)
    setRows(toRows(res.bands))
  }

  useEffect(() => {
    api
      .get<GradeScaleResponse>('/api/v1/grade-scale')
      .then(apply)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the grade scale.'))
  }, [])

  function updateRow(index: number, patch: Partial<BandRow>) {
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
    setSaved(false)
  }

  async function handleSave() {
    setError(null)
    setSaved(false)
    setSaving(true)
    try {
      const res = await api.put<GradeScaleResponse>('/api/v1/grade-scale', {
        bands: rows.map((r) => ({
          grade: r.grade.trim(),
          minPercentage: Number(r.min),
          maxPercentage: Number(r.max),
          remark: r.remark.trim() || undefined,
        })),
      })
      apply(res)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the grade scale.')
    } finally {
      setSaving(false)
    }
  }

  async function handleReset() {
    if (!confirm('Go back to the default CBSE-style grade scale? Your custom bands will be removed.')) return
    setError(null)
    setSaved(false)
    try {
      apply(await api.del<GradeScaleResponse>('/api/v1/grade-scale'))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset the grade scale.')
    }
  }

  return (
    <section>
      <h2 className="font-display text-xl text-ink mb-1">Grade scale</h2>
      <p className="text-xs text-slate-light mb-3">
        {scale?.usingDefaults
          ? 'Using the default CBSE-style scale.'
          : 'Using your custom scale.'}{' '}
        Bands must not overlap and must cover 0% to 100%.
      </p>

      {error && <ErrorBanner message={error} />}
      {saved && (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
          Grade scale saved.
        </div>
      )}

      {!scale ? (
        <LedgerSkeleton columns={4} />
      ) : !canEdit ? (
        <Ledger columns={['Grade', 'From %', 'To %', 'Remark']}>
          {scale.bands.map((b) => (
            <Row key={b.grade}>
              <Cell className="font-medium">{b.grade}</Cell>
              <Cell>{b.minPercentage}</Cell>
              <Cell>{b.maxPercentage}</Cell>
              <Cell>{b.remark ?? '\u2014'}</Cell>
            </Row>
          ))}
        </Ledger>
      ) : (
        <>
          <div className="space-y-2 mb-4">
            {rows.map((r, i) => (
              <div key={i} className="grid grid-cols-[5rem_5rem_5rem_1fr_auto] gap-2 items-center">
                <Input aria-label="Grade" placeholder="Grade" value={r.grade} onChange={(e) => updateRow(i, { grade: e.target.value })} />
                <Input aria-label="From %" type="number" min="0" max="100" step="0.5" placeholder="From" value={r.min} onChange={(e) => updateRow(i, { min: e.target.value })} />
                <Input aria-label="To %" type="number" min="0" max="100" step="0.5" placeholder="To" value={r.max} onChange={(e) => updateRow(i, { max: e.target.value })} />
                <Input aria-label="Remark" placeholder="Remark (optional)" value={r.remark} onChange={(e) => updateRow(i, { remark: e.target.value })} />
                <button
                  onClick={() => setRows((prev) => prev.filter((_, idx) => idx !== i))}
                  disabled={rows.length <= 1}
                  className="text-xs text-danger hover:opacity-80 underline underline-offset-2 disabled:opacity-40"
                >
                  Remove
                </button>
              </div>
            ))}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => setRows((prev) => [...prev, { grade: '', min: '', max: '', remark: '' }])}>
              Add band
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? 'Saving…' : 'Save scale'}
            </Button>
            {!scale.usingDefaults && (
              <Button variant="ghost" onClick={handleReset}>
                Reset to defaults
              </Button>
            )}
          </div>
        </>
      )}
    </section>
  )
}
