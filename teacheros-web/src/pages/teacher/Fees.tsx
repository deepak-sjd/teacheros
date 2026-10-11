import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Fee, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Modal, Row, Select, StatusPill } from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'
import { StudentPicker } from '@/components/StudentPicker'
import { ReceiptsModal } from '@/components/ReceiptsModal'
import { formatCurrency, formatDate, todayIso } from '@/lib/format'

export function Fees() {
  const [data, setData] = useState<Page<Fee> | null>(null)
  const [page, setPage] = useState(0)
  // studentId -> name, resolved via /students/lookup for exactly the
  // students on this page of fees (not a capped first-100 student list).
  const [names, setNames] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [payFee, setPayFee] = useState<Fee | null>(null)
  const [receiptFee, setReceiptFee] = useState<Fee | null>(null)

  function load(p = page) {
    setLoading(true)
    setError(null)
    api
      .get<Page<Fee>>('/api/v1/fees/pending', { page: p, size: PAGE_SIZE })
      .then(async (res) => {
        // A payment can empty the last page - step back one page.
        if (res.content.length === 0 && p > 0) {
          setPage(p - 1)
          return
        }
        setData(res)
        const ids = [...new Set(res.content.map((fee) => fee.studentId))]
        if (ids.length === 0) {
          setNames({})
          return
        }
        const students = await api.get<Student[]>('/api/v1/students/lookup', { ids: ids.join(',') })
        setNames(Object.fromEntries(students.map((s) => [s.id, s.fullName])))
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load fees.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load(page)
  }, [page])

  const studentName = (id: string) => names[id] ?? id.slice(0, 8)

  return (
    <Layout>
      <PageHeader
        title="Fees"
        description="Fees due across all students, earliest due date first. Fully paid fees drop off this list."
        action={<Button onClick={() => setCreateOpen(true)}>New fee</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {loading && !data ? (
        <LedgerSkeleton columns={7} />
      ) : !data || data.totalElements === 0 ? (
        <EmptyState
          title="Nothing pending"
          description="Every fee is fully paid."
          action={<Button onClick={() => setCreateOpen(true)}>New fee</Button>}
        />
      ) : (
        <>
          <Ledger columns={['Student', 'Due date', 'Amount due', 'Paid', 'Balance', 'Status', '']}>
            {data.content.map((f) => (
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
                  <div className="flex items-center gap-3 whitespace-nowrap">
                    <button onClick={() => setPayFee(f)} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                      Record payment
                    </button>
                    {f.amountPaid > 0 && (
                      <button
                        onClick={() => setReceiptFee(f)}
                        className="text-sm text-slate hover:text-ink underline underline-offset-2"
                      >
                        Receipts
                      </button>
                    )}
                  </div>
                </Cell>
              </Row>
            ))}
          </Ledger>
          <Pagination
            page={data.number}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            size={data.size}
            onChange={setPage}
          />
        </>
      )}

      <NewFeeModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          load(page)
        }}
      />
      <PaymentModal
        fee={payFee}
        onClose={() => setPayFee(null)}
        onSaved={() => {
          setPayFee(null)
          load(page)
        }}
      />
      <ReceiptsModal fee={receiptFee} onClose={() => setReceiptFee(null)} />
    </Layout>
  )
}

function NewFeeModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: () => void
}) {
  const [student, setStudent] = useState<Student | null>(null)
  const [amountDue, setAmountDue] = useState('')
  const [dueDate, setDueDate] = useState(todayIso())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!student) {
      setError('Choose a student.')
      return
    }
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/fees', { studentId: student.id, amountDue: Number(amountDue), dueDate })
      setStudent(null)
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
        <StudentPicker value={student} onChange={setStudent} />
        <Input label="Amount due (₹)" type="number" min="1" step="0.01" value={amountDue} onChange={(e) => setAmountDue(e.target.value)} required />
        <Input label="Due date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving || !student}>
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
