import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { FeeAmountType, FeeDiscount, FeeHead, FeeRules, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Modal, Row, Select } from '@/components/ui'
import { formatDate } from '@/lib/format'

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)
const show = (type: FeeAmountType, value: number) => (type === 'PERCENT' ? `${value}%` : `₹${value}`)

export function FeeDiscounts() {
  return (
    <Layout>
      <PageHeader
        title="Discounts & rules"
        description="Per-student discounts, the sibling concession and late-fee rules. Changes apply to fees generated afterwards."
      />
      <div className="space-y-12">
        <RulesSection />
        <DiscountsSection />
      </div>
    </Layout>
  )
}

function RulesSection() {
  const [rules, setRules] = useState<FeeRules | null>(null)
  const [sibType, setSibType] = useState<FeeAmountType>('NONE')
  const [sibValue, setSibValue] = useState('0')
  const [lateType, setLateType] = useState<FeeAmountType>('NONE')
  const [lateValue, setLateValue] = useState('0')
  const [grace, setGrace] = useState('0')
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    api
      .get<FeeRules>('/api/v1/fee-rules')
      .then((r) => {
        setRules(r)
        setSibType(r.siblingConcessionType)
        setSibValue(String(r.siblingConcessionValue))
        setLateType(r.lateFeeType)
        setLateValue(String(r.lateFeeValue))
        setGrace(String(r.lateFeeGraceDays))
      })
      .catch((e) => setError(msg(e, 'Could not load the rules.')))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setNote(null)
    setSaving(true)
    try {
      const r = await api.put<FeeRules>('/api/v1/fee-rules', {
        siblingConcessionType: sibType,
        siblingConcessionValue: Number(sibValue) || 0,
        lateFeeType: lateType,
        lateFeeValue: Number(lateValue) || 0,
        lateFeeGraceDays: Number(grace) || 0,
      })
      setRules(r)
      setNote('Rules saved.')
    } catch (err) {
      setError(msg(err, 'Could not save the rules.'))
    } finally {
      setSaving(false)
    }
  }

  async function runLateFees() {
    setError(null)
    setNote(null)
    try {
      const res = await api.post<{ created: number }>('/api/v1/fee-rules/late-fees/run')
      setNote(res.created === 0 ? 'No new late fees were due.' : `Added ${res.created} late fee${res.created === 1 ? '' : 's'}.`)
    } catch (err) {
      setError(msg(err, 'Could not run late fees.'))
    }
  }

  const types: FeeAmountType[] = ['NONE', 'FIXED', 'PERCENT']
  const label = (t: FeeAmountType) => (t === 'NONE' ? 'Off' : t === 'FIXED' ? 'Fixed amount (₹)' : 'Percent (%)')

  return (
    <section className="max-w-xl">
      <h2 className="font-display text-xl text-ink mb-1">Tenant rules</h2>
      <p className="text-sm text-slate mb-4">
        Sibling concession applies to the second and later children sharing a guardian phone. Late fee is added once per
        overdue instalment, nightly at 2:30 AM IST.
      </p>
      {error && <ErrorBanner message={error} />}
      {note && <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">{note}</div>}
      {rules === null ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : (
        <form onSubmit={save} className="space-y-4 rounded-lg border border-line bg-white p-5">
          <div className="grid grid-cols-2 gap-3">
            <Select label="Sibling concession" value={sibType} onChange={(e) => setSibType(e.target.value as FeeAmountType)}>
              {types.map((t) => (
                <option key={t} value={t}>
                  {label(t)}
                </option>
              ))}
            </Select>
            <Input label="Value" type="number" min="0" step="0.01" value={sibValue} onChange={(e) => setSibValue(e.target.value)} disabled={sibType === 'NONE'} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Select label="Late fee" value={lateType} onChange={(e) => setLateType(e.target.value as FeeAmountType)}>
              {types.map((t) => (
                <option key={t} value={t}>
                  {label(t)}
                </option>
              ))}
            </Select>
            <Input label="Value" type="number" min="0" step="0.01" value={lateValue} onChange={(e) => setLateValue(e.target.value)} disabled={lateType === 'NONE'} />
          </div>
          <Input label="Grace days after the due date" type="number" min="0" max="365" value={grace} onChange={(e) => setGrace(e.target.value)} />
          <div className="flex gap-2 justify-between pt-2">
            <Button type="button" variant="secondary" onClick={runLateFees}>
              Run late fees now
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save rules'}
            </Button>
          </div>
        </form>
      )}
    </section>
  )
}

function DiscountsSection() {
  const [items, setItems] = useState<FeeDiscount[] | null>(null)
  const [students, setStudents] = useState<Student[]>([])
  const [heads, setHeads] = useState<FeeHead[]>([])
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [studentId, setStudentId] = useState('')
  const [headId, setHeadId] = useState('')
  const [type, setType] = useState<FeeAmountType>('PERCENT')
  const [value, setValue] = useState('')
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  function load() {
    Promise.all([
      api.get<FeeDiscount[]>('/api/v1/fee-discounts'),
      api.get<Page<Student>>('/api/v1/students', { size: 200 }),
      api.get<FeeHead[]>('/api/v1/fee-heads'),
    ])
      .then(([d, s, h]) => {
        setItems(d)
        setStudents(s.content)
        setHeads(h.filter((x) => x.active))
      })
      .catch((e) => setError(msg(e, 'Could not load discounts.')))
  }
  useEffect(load, [])

  async function create(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/fee-discounts', {
        studentId,
        feeHeadId: headId || undefined,
        discountType: type,
        value: Number(value),
        reason,
      })
      setOpen(false)
      setValue('')
      setReason('')
      load()
    } catch (err) {
      setError(msg(err, 'Could not create the discount.'))
    } finally {
      setSaving(false)
    }
  }

  async function revoke(d: FeeDiscount) {
    setError(null)
    try {
      await api.patch(`/api/v1/fee-discounts/${d.id}`, {
        discountType: d.discountType,
        value: d.value,
        reason: d.reason,
        active: !d.active,
      })
      load()
    } catch (err) {
      setError(msg(err, 'Could not update the discount.'))
    }
  }

  return (
    <section>
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-display text-xl text-ink">Student discounts</h2>
        <Button onClick={() => setOpen(true)}>New discount</Button>
      </div>
      {error && <ErrorBanner message={error} />}
      {items === null ? (
        <LedgerSkeleton columns={6} />
      ) : items.length === 0 ? (
        <EmptyState title="No discounts yet" />
      ) : (
        <Ledger columns={['Student', 'Applies to', 'Discount', 'Reason', 'Created', 'Status', '']}>
          {items.map((d) => (
            <Row key={d.id}>
              <Cell className="font-medium">{d.studentName}</Cell>
              <Cell>{d.feeHeadName}</Cell>
              <Cell>{show(d.discountType, d.value)}</Cell>
              <Cell>{d.reason}</Cell>
              <Cell>{formatDate(d.createdAt)}</Cell>
              <Cell>{d.active ? 'Active' : 'Revoked'}</Cell>
              <Cell>
                <button onClick={() => revoke(d)} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                  {d.active ? 'Revoke' : 'Reinstate'}
                </button>
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}
      <Modal open={open} onClose={() => setOpen(false)} title="New discount">
        <form onSubmit={create} className="space-y-4">
          <Select label="Student" value={studentId} onChange={(e) => setStudentId(e.target.value)} required>
            <option value="">Choose a student</option>
            {students.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}
              </option>
            ))}
          </Select>
          <Select label="Applies to" value={headId} onChange={(e) => setHeadId(e.target.value)}>
            <option value="">All fee heads</option>
            {heads.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name}
              </option>
            ))}
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <Select label="Type" value={type} onChange={(e) => setType(e.target.value as FeeAmountType)}>
              <option value="PERCENT">Percent (%)</option>
              <option value="FIXED">Fixed amount (₹)</option>
            </Select>
            <Input label="Value" type="number" min="0.01" step="0.01" value={value} onChange={(e) => setValue(e.target.value)} required />
          </div>
          <Input label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required />
          <p className="text-xs text-slate">Applies to fees generated from now on; it does not change fees already created.</p>
          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Create discount'}
            </Button>
          </div>
        </form>
      </Modal>
    </section>
  )
}
