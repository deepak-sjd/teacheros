import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Fee, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, Modal, Row, Select, StatusPill } from '@/components/ui'
import { formatCurrency, formatDate, todayIso } from '@/lib/format'

export function Fees() {
  const [pending, setPending] = useState<Fee[]>([])
  const [students, setStudents] = useState<Student[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [payFee, setPayFee] = useState<Fee | null>(null)

  function load() {
    setLoading(true)
    Promise.all([
      api.get<Page<Fee>>('/api/v1/fees/pending', { size: 100 }),
      api.get<Page<Student>>('/api/v1/students', { size: 100 }),
    ])
      .then(([f, s]) => {
        setPending(f.content)
        setStudents(s.content)
      })
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const studentName = (id: string) => students.find((s) => s.id === id)?.fullName ?? id.slice(0, 8)

  return (
    <Layout>
      <PageHeader
        title="Fees"
        description="Fees due across all students. Fully paid fees drop off this list."
        action={<Button onClick={() => setCreateOpen(true)}>New fee</Button>}
      />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : pending.length === 0 ? (
        <EmptyState
          title="Nothing pending"
          description="Every fee is fully paid."
          action={<Button onClick={() => setCreateOpen(true)}>New fee</Button>}
        />
      ) : (
        <Ledger columns={['Student', 'Due date', 'Amount due', 'Paid', 'Balance', 'Status', '']}>
          {pending.map((f) => (
            <Row key={f.id}>
              <Cell className="font-medium">{studentName(f.studentId)}</Cell>
              <Cell>{formatDate(f.dueDate)}</Cell>
              <Cell>{formatCurrency(f.amountDue)}</Cell>
              <Cell>{formatCurrency(f.amountPaid)}</Cell>
              <Cell className="font-medium">{formatCurrency(f.amountDue - f.amountPaid)}</Cell>
              <Cell>
                <StatusPill status={f.status} />
              </Cell>
              <Cell>
                <button onClick={() => setPayFee(f)} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                  Record payment
                </button>
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}

      <NewFeeModal open={createOpen} students={students} onClose={() => setCreateOpen(false)} onCreated={() => { setCreateOpen(false); load() }} />
      <PaymentModal fee={payFee} onClose={() => setPayFee(null)} onSaved={() => { setPayFee(null); load() }} />
    </Layout>
  )
}

function NewFeeModal({
  open,
  students,
  onClose,
  onCreated,
}: {
  open: boolean
  students: Student[]
  onClose: () => void
  onCreated: () => void
}) {
  const [studentId, setStudentId] = useState('')
  const [amountDue, setAmountDue] = useState('')
  const [dueDate, setDueDate] = useState(todayIso())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/fees', { studentId, amountDue: Number(amountDue), dueDate })
      setStudentId('')
      setAmountDue('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the fee.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New fee">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Select label="Student" value={studentId} onChange={(e) => setStudentId(e.target.value)} required>
          <option value="">Choose a student</option>
          {students.map((s) => (
            <option key={s.id} value={s.id}>
              {s.fullName}
            </option>
          ))}
        </Select>
        <Input label="Amount due (₹)" type="number" min="1" step="0.01" value={amountDue} onChange={(e) => setAmountDue(e.target.value)} required />
        <Input label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create fee'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function PaymentModal({ fee, onClose, onSaved }: { fee: Fee | null; onClose: () => void; onSaved: () => void }) {
  const [amount, setAmount] = useState('')
  const [method, setMethod] = useState('Cash')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (fee) setAmount(String(fee.amountDue - fee.amountPaid))
  }, [fee])

  if (!fee) return null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!fee) return
    setError(null)
    setSaving(true)
    try {
      await api.post(`/api/v1/fees/${fee.id}/payments`, { amountPaid: Number(amount), method })
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not record the payment.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={!!fee} onClose={onClose} title="Record payment">
      {error && <ErrorBanner message={error} />}
      <p className="text-sm text-slate mb-4">
        Balance due: <span className="text-ink font-medium">{formatCurrency(fee.amountDue - fee.amountPaid)}</span>
      </p>
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Amount paid (₹)" type="number" min="1" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        <Select label="Method" value={method} onChange={(e) => setMethod(e.target.value)}>
          <option>Cash</option>
          <option>UPI</option>
          <option>Bank transfer</option>
          <option>Card</option>
        </Select>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Record payment'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
