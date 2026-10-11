import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { AssignmentPreview, AssignmentResult, FeeStructure, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Ledger, Row, Select, StatBlock } from '@/components/ui'
import { formatCurrency, formatDate } from '@/lib/format'

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

/** Step 1 pick structure (and optionally students) -> 2 preview -> 3 confirm. */
export function AssignFees() {
  const [structures, setStructures] = useState<FeeStructure[]>([])
  const [structureId, setStructureId] = useState('')
  const [pickStudents, setPickStudents] = useState(false)
  const [students, setStudents] = useState<Student[]>([])
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [preview, setPreview] = useState<AssignmentPreview | null>(null)
  const [result, setResult] = useState<AssignmentResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api
      .get<FeeStructure[]>('/api/v1/fee-structures')
      .then((s) => setStructures(s.filter((x) => x.active)))
      .catch((e) => setError(msg(e, 'Could not load fee structures.')))
  }, [])

  useEffect(() => {
    if (!pickStudents || students.length > 0) return
    api.get<Page<Student>>('/api/v1/students', { size: 200 }).then((p) => setStudents(p.content))
  }, [pickStudents, students.length])

  const body = () => ({
    structureId,
    studentIds: pickStudents && selected.size > 0 ? [...selected] : undefined,
  })

  async function runPreview() {
    setError(null)
    setResult(null)
    setBusy(true)
    try {
      setPreview(await api.post<AssignmentPreview>('/api/v1/fee-structures/preview', body()))
    } catch (err) {
      setPreview(null)
      setError(msg(err, 'Could not build the preview.'))
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    setError(null)
    setBusy(true)
    try {
      setResult(await api.post<AssignmentResult>('/api/v1/fee-structures/assign', body()))
      setPreview(null)
    } catch (err) {
      setError(msg(err, 'Could not assign the structure.'))
    } finally {
      setBusy(false)
    }
  }

  function toggle(id: string) {
    const next = new Set(selected)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setSelected(next)
    setPreview(null)
  }

  return (
    <Layout>
      <PageHeader
        title="Assign fees"
        description="Pick a structure, review exactly what will be charged, then confirm. Running it again never duplicates fees."
      />

      {error && <ErrorBanner message={error} />}

      {result && (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-6 border border-success/20">
          Created {result.created} fee{result.created === 1 ? '' : 's'} for {result.studentCount} student
          {result.studentCount === 1 ? '' : 's'} ({formatCurrency(result.totalCharged)}).
          {result.skipped > 0 && ` ${result.skipped} already existed and were left alone.`}
        </div>
      )}

      <div className="max-w-xl space-y-4 mb-8">
        <Select
          label="1. Fee structure"
          value={structureId}
          onChange={(e) => {
            setStructureId(e.target.value)
            setPreview(null)
            setResult(null)
          }}
        >
          <option value="">Choose a structure</option>
          {structures.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.academicYear}) — {formatCurrency(s.totalAmount)}
            </option>
          ))}
        </Select>

        <label className="flex items-center gap-2 text-sm text-ink">
          <input type="checkbox" checked={pickStudents} onChange={(e) => { setPickStudents(e.target.checked); setPreview(null) }} />
          Choose specific students (otherwise the structure's batch / grade)
        </label>

        {pickStudents && (
          <div className="max-h-56 overflow-y-auto rounded-lg border border-line bg-white p-3 space-y-1.5">
            {students.map((s) => (
              <label key={s.id} className="flex items-center gap-2 text-sm text-ink">
                <input type="checkbox" checked={selected.has(s.id)} onChange={() => toggle(s.id)} />
                {s.fullName}
                {s.grade ? <span className="text-xs text-slate-light">Grade {s.grade}</span> : null}
              </label>
            ))}
          </div>
        )}

        <Button onClick={runPreview} disabled={!structureId || busy}>
          {busy && !preview ? 'Working…' : '2. Preview'}
        </Button>
      </div>

      {preview &&
        (preview.rows.length === 0 ? (
          <EmptyState title="No students matched" description="Check the structure's batch or grade, or choose students." />
        ) : (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-6">
              <StatBlock label="Students" value={String(preview.studentCount)} />
              <StatBlock label="Fees to create" value={String(preview.feesToCreate)} sub={`${preview.feesAlreadyAssigned} already assigned`} />
              <StatBlock label="Total to charge" value={formatCurrency(preview.totalToCharge)} />
              <StatBlock label="Total discounts" value={formatCurrency(preview.totalDiscount)} />
            </div>

            <Ledger columns={['Student', 'Instalment', 'Due date', 'Gross', 'Discount', 'Amount', 'Note']}>
              {preview.rows.map((r) => (
                <Row key={`${r.studentId}-${r.instalmentNo}`}>
                  <Cell className="font-medium">{r.studentName}</Cell>
                  <Cell>#{r.instalmentNo}</Cell>
                  <Cell>{formatDate(r.dueDate)}</Cell>
                  <Cell>{formatCurrency(r.grossAmount)}</Cell>
                  <Cell className={r.discountAmount > 0 ? 'text-success' : ''}>
                    {r.discountAmount > 0 ? `− ${formatCurrency(r.discountAmount)}` : '\u2014'}
                  </Cell>
                  <Cell className="font-medium">{formatCurrency(r.amountDue)}</Cell>
                  <Cell>{r.alreadyAssigned ? 'Already assigned' : r.discountNotes || '\u2014'}</Cell>
                </Row>
              ))}
            </Ledger>

            <div className="mt-5 flex justify-end">
              <Button onClick={confirm} disabled={busy || preview.feesToCreate === 0}>
                {busy ? 'Assigning…' : preview.feesToCreate === 0 ? 'Nothing to create' : `3. Confirm and create ${preview.feesToCreate} fees`}
              </Button>
            </div>
          </>
        ))}
    </Layout>
  )
}
