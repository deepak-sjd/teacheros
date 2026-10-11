import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, FeeHead, FeeStructure, InstalmentPlan, Page } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Modal, Row, Select } from '@/components/ui'
import { formatCurrency, formatDate } from '@/lib/format'

type Tab = 'heads' | 'plans' | 'structures'

const msg = (err: unknown, fallback: string) => (err instanceof ApiError ? err.message : fallback)

export function FeeSetup() {
  const [tab, setTab] = useState<Tab>('heads')
  const tabs: [Tab, string][] = [
    ['heads', 'Fee heads'],
    ['plans', 'Instalment plans'],
    ['structures', 'Fee structures'],
  ]
  return (
    <Layout>
      <PageHeader title="Fee setup" description="Fee heads, instalment plans and the structures built from them." />
      <div className="flex gap-2 mb-6">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium border transition-colors ${
              tab === key ? 'bg-ink text-paper border-ink' : 'bg-white text-slate border-line hover:border-ink'
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      {tab === 'heads' && <HeadsTab />}
      {tab === 'plans' && <PlansTab />}
      {tab === 'structures' && <StructuresTab />}
    </Layout>
  )
}

// ---------------------------------------------------------------- heads

function HeadsTab() {
  const [heads, setHeads] = useState<FeeHead[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [eligible, setEligible] = useState(true)
  const [saving, setSaving] = useState(false)

  function load() {
    api.get<FeeHead[]>('/api/v1/fee-heads').then(setHeads).catch((e) => setError(msg(e, 'Could not load fee heads.')))
  }
  useEffect(load, [])

  async function create(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/fee-heads', { name, code: code || undefined, siblingConcessionEligible: eligible })
      setOpen(false)
      setName('')
      setCode('')
      setEligible(true)
      load()
    } catch (err) {
      setError(msg(err, 'Could not create the fee head.'))
    } finally {
      setSaving(false)
    }
  }

  async function toggleActive(h: FeeHead) {
    setError(null)
    try {
      await api.patch(`/api/v1/fee-heads/${h.id}`, {
        name: h.name,
        code: h.code ?? undefined,
        siblingConcessionEligible: h.siblingConcessionEligible,
        active: !h.active,
        sortOrder: h.sortOrder,
      })
      load()
    } catch (err) {
      setError(msg(err, 'Could not update the fee head.'))
    }
  }

  return (
    <>
      <div className="flex justify-end mb-4">
        <Button onClick={() => setOpen(true)}>New fee head</Button>
      </div>
      {error && <ErrorBanner message={error} />}
      {heads === null ? (
        <LedgerSkeleton columns={4} />
      ) : heads.length === 0 ? (
        <EmptyState title="No fee heads yet" description="Add heads like Tuition, Admission, Transport, Exam, Lab." />
      ) : (
        <Ledger columns={['Name', 'Code', 'Sibling concession', 'Status', '']}>
          {heads.map((h) => (
            <Row key={h.id}>
              <Cell className="font-medium">{h.name}</Cell>
              <Cell>{h.code ?? '\u2014'}</Cell>
              <Cell>{h.siblingConcessionEligible ? 'Eligible' : 'Not eligible'}</Cell>
              <Cell>{h.active ? 'Active' : 'Inactive'}</Cell>
              <Cell>
                <button onClick={() => toggleActive(h)} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                  {h.active ? 'Deactivate' : 'Activate'}
                </button>
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}
      <Modal open={open} onClose={() => setOpen(false)} title="New fee head">
        <form onSubmit={create} className="space-y-4">
          <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} required />
          <Input label="Code (optional)" value={code} onChange={(e) => setCode(e.target.value)} />
          <label className="flex items-center gap-2 text-sm text-ink">
            <input type="checkbox" checked={eligible} onChange={(e) => setEligible(e.target.checked)} />
            Sibling concession applies to this head
          </label>
          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Create'}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  )
}

// ---------------------------------------------------------------- plans

interface PlanRowInput {
  dueDate: string
  percentage: string
}

// Integer cents so the split always adds to exactly 100.00.
function evenSplit(n: number): string[] {
  const base = Math.floor(10000 / n)
  const out = Array.from({ length: n }, () => (base / 100).toFixed(2))
  out[n - 1] = ((10000 - base * (n - 1)) / 100).toFixed(2)
  return out
}

function PlansTab() {
  const [plans, setPlans] = useState<InstalmentPlan[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [rows, setRows] = useState<PlanRowInput[]>([{ dueDate: '', percentage: '100.00' }])
  const [saving, setSaving] = useState(false)

  function load() {
    api.get<InstalmentPlan[]>('/api/v1/instalment-plans').then(setPlans).catch((e) => setError(msg(e, 'Could not load plans.')))
  }
  useEffect(load, [])

  const totalCents = rows.reduce((sum, r) => sum + Math.round((parseFloat(r.percentage) || 0) * 100), 0)

  function setCount(n: number) {
    const split = evenSplit(n)
    setRows(Array.from({ length: n }, (_, i) => ({ dueDate: rows[i]?.dueDate ?? '', percentage: split[i] })))
  }

  async function create(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (totalCents !== 10000) {
      setError('Percentages must add up to exactly 100.')
      return
    }
    setSaving(true)
    try {
      await api.post('/api/v1/instalment-plans', {
        name,
        instalments: rows.map((r) => ({ dueDate: r.dueDate, percentage: Number(r.percentage) })),
      })
      setOpen(false)
      setName('')
      setRows([{ dueDate: '', percentage: '100.00' }])
      load()
    } catch (err) {
      setError(msg(err, 'Could not create the plan.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="flex justify-end mb-4">
        <Button onClick={() => setOpen(true)}>New instalment plan</Button>
      </div>
      {error && <ErrorBanner message={error} />}
      {plans === null ? (
        <LedgerSkeleton columns={3} />
      ) : plans.length === 0 ? (
        <EmptyState title="No instalment plans yet" description="A plan lists due dates and what percentage is due on each." />
      ) : (
        <Ledger columns={['Plan', 'Instalments', 'Schedule']}>
          {plans.map((p) => (
            <Row key={p.id}>
              <Cell className="font-medium">{p.name}</Cell>
              <Cell>{p.numberOfInstalments}</Cell>
              <Cell>{p.instalments.map((i) => `${formatDate(i.dueDate)} (${i.percentage}%)`).join(' · ')}</Cell>
            </Row>
          ))}
        </Ledger>
      )}
      <Modal open={open} onClose={() => setOpen(false)} title="New instalment plan">
        <form onSubmit={create} className="space-y-4">
          <Input label="Plan name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. 4 instalments" required />
          <Select label="Number of instalments" value={rows.length} onChange={(e) => setCount(Number(e.target.value))}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </Select>
          <div className="space-y-2">
            {rows.map((r, i) => (
              <div key={i} className="grid grid-cols-[2rem_1fr_6rem] gap-2 items-end">
                <span className="text-xs text-slate pb-2">#{i + 1}</span>
                <Input
                  label={i === 0 ? 'Due date' : undefined}
                  type="date"
                  value={r.dueDate}
                  onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, dueDate: e.target.value } : x)))}
                  required
                />
                <Input
                  label={i === 0 ? '%' : undefined}
                  type="number"
                  step="0.01"
                  min="0.01"
                  max="100"
                  value={r.percentage}
                  onChange={(e) => setRows(rows.map((x, j) => (j === i ? { ...x, percentage: e.target.value } : x)))}
                  required
                />
              </div>
            ))}
          </div>
          <p className={`text-xs ${totalCents === 10000 ? 'text-success' : 'text-danger'}`}>
            Total: {(totalCents / 100).toFixed(2)}% (must be 100)
          </p>
          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Create plan'}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  )
}

// ----------------------------------------------------------- structures

interface ItemInput {
  headId: string
  amount: string
}

function StructuresTab() {
  const [structures, setStructures] = useState<FeeStructure[] | null>(null)
  const [heads, setHeads] = useState<FeeHead[]>([])
  const [plans, setPlans] = useState<InstalmentPlan[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [year, setYear] = useState('2026-27')
  const [batchId, setBatchId] = useState('')
  const [grade, setGrade] = useState('')
  const [planId, setPlanId] = useState('')
  const [items, setItems] = useState<ItemInput[]>([{ headId: '', amount: '' }])
  const [saving, setSaving] = useState(false)

  function load() {
    Promise.all([
      api.get<FeeStructure[]>('/api/v1/fee-structures'),
      api.get<FeeHead[]>('/api/v1/fee-heads'),
      api.get<InstalmentPlan[]>('/api/v1/instalment-plans'),
      api.get<Page<Batch>>('/api/v1/batches', { size: 100 }),
    ])
      .then(([s, h, p, b]) => {
        setStructures(s)
        setHeads(h.filter((x) => x.active))
        setPlans(p)
        setBatches(b.content)
      })
      .catch((e) => setError(msg(e, 'Could not load fee structures.')))
  }
  useEffect(load, [])

  async function create(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/fee-structures', {
        name,
        academicYear: year,
        grade: grade || undefined,
        batchId: batchId || undefined,
        planId,
        items: items.map((i) => ({ headId: i.headId, amount: Number(i.amount) })),
      })
      setOpen(false)
      setName('')
      setItems([{ headId: '', amount: '' }])
      load()
    } catch (err) {
      setError(msg(err, 'Could not create the structure.'))
    } finally {
      setSaving(false)
    }
  }

  async function toggleActive(s: FeeStructure) {
    setError(null)
    try {
      await api.patch(`/api/v1/fee-structures/${s.id}`, { name: s.name, active: !s.active })
      load()
    } catch (err) {
      setError(msg(err, 'Could not update the structure.'))
    }
  }

  const batchName = (id: string | null) => batches.find((b) => b.id === id)?.name

  return (
    <>
      <div className="flex justify-end mb-4">
        <Button onClick={() => setOpen(true)} disabled={heads.length === 0 || plans.length === 0}>
          New fee structure
        </Button>
      </div>
      {heads.length === 0 || plans.length === 0 ? (
        <p className="text-xs text-slate mb-4">Create at least one fee head and one instalment plan first.</p>
      ) : null}
      {error && <ErrorBanner message={error} />}
      {structures === null ? (
        <LedgerSkeleton columns={6} />
      ) : structures.length === 0 ? (
        <EmptyState title="No fee structures yet" />
      ) : (
        <Ledger columns={['Name', 'Year', 'Applies to', 'Heads', 'Total', 'Plan', '']}>
          {structures.map((s) => (
            <Row key={s.id}>
              <Cell className="font-medium">
                {s.name}
                {!s.active && <span className="ml-2 text-xs text-slate-light">(inactive)</span>}
              </Cell>
              <Cell>{s.academicYear}</Cell>
              <Cell>{[batchName(s.batchId), s.grade ? `Grade ${s.grade}` : null].filter(Boolean).join(' · ') || '\u2014'}</Cell>
              <Cell>{s.items.map((i) => `${i.headName} ${formatCurrency(i.amount)}`).join(', ')}</Cell>
              <Cell className="font-medium">{formatCurrency(s.totalAmount)}</Cell>
              <Cell>{s.planName ?? '\u2014'}</Cell>
              <Cell>
                <button onClick={() => toggleActive(s)} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                  {s.active ? 'Deactivate' : 'Activate'}
                </button>
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}
      <Modal open={open} onClose={() => setOpen(false)} title="New fee structure">
        <form onSubmit={create} className="space-y-4">
          <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Class 8 annual fee" required />
          <div className="grid grid-cols-2 gap-3">
            <Input label="Academic year" value={year} onChange={(e) => setYear(e.target.value)} placeholder="2026-27" required />
            <Input label="Grade (optional)" value={grade} onChange={(e) => setGrade(e.target.value)} placeholder="e.g. 8" />
          </div>
          <Select label="Batch (optional)" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
            <option value="">Any batch</option>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </Select>
          <Select label="Instalment plan" value={planId} onChange={(e) => setPlanId(e.target.value)} required>
            <option value="">Choose a plan</option>
            {plans.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
          <div className="space-y-2">
            <span className="block text-sm font-medium text-ink">Fee heads</span>
            {items.map((it, i) => (
              <div key={i} className="grid grid-cols-[1fr_7rem_auto] gap-2 items-end">
                <Select value={it.headId} onChange={(e) => setItems(items.map((x, j) => (j === i ? { ...x, headId: e.target.value } : x)))} required>
                  <option value="">Head</option>
                  {heads.map((h) => (
                    <option key={h.id} value={h.id}>
                      {h.name}
                    </option>
                  ))}
                </Select>
                <Input
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="₹ annual"
                  value={it.amount}
                  onChange={(e) => setItems(items.map((x, j) => (j === i ? { ...x, amount: e.target.value } : x)))}
                  required
                />
                {items.length > 1 && (
                  <button type="button" onClick={() => setItems(items.filter((_, j) => j !== i))} className="text-xs text-danger pb-2">
                    Remove
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={() => setItems([...items, { headId: '', amount: '' }])} className="text-sm text-slate underline underline-offset-2">
              Add head
            </button>
          </div>
          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Create structure'}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  )
}
