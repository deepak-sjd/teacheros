import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import type { Batch, Fee, Page, Student } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { StatBlock, Button, Ledger, Row, Cell, EmptyState } from '@/components/ui'
import { formatCurrency } from '@/lib/format'

export function Overview() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [students, setStudents] = useState<Page<Student> | null>(null)
  const [pendingFees, setPendingFees] = useState<Page<Fee> | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      api.get<Page<Batch>>('/api/v1/batches', { size: 50 }),
      api.get<Page<Student>>('/api/v1/students', { size: 50 }),
      api.get<Page<Fee>>('/api/v1/fees/pending', { size: 10 }),
    ])
      .then(([b, s, f]) => {
        setBatches(b.content)
        setStudents(s)
        setPendingFees(f)
      })
      .finally(() => setLoading(false))
  }, [])

  const totalDue = (pendingFees?.content ?? []).reduce((sum, f) => sum + (f.amountDue - f.amountPaid), 0)

  return (
    <Layout>
      <PageHeader title="Overview" description="Today's snapshot across your batches, students, and fees." />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-10">
            <StatBlock label="Active batches" value={String(batches.length)} />
            <StatBlock label="Students enrolled" value={String(students?.totalElements ?? 0)} />
            <StatBlock
              label="Fees pending"
              value={formatCurrency(totalDue)}
              sub={`${pendingFees?.totalElements ?? 0} student${(pendingFees?.totalElements ?? 0) === 1 ? '' : 's'}`}
            />
          </div>

          <div className="flex items-center justify-between mb-3">
            <h2 className="font-display text-xl text-ink">Fees due soon</h2>
            <Link to="/app/fees" className="text-sm text-slate hover:text-ink underline underline-offset-2">
              View all
            </Link>
          </div>

          {pendingFees && pendingFees.content.length > 0 ? (
            <Ledger columns={['Student', 'Due date', 'Amount due', 'Balance']}>
              {pendingFees.content.slice(0, 6).map((f) => (
                <Row key={f.id}>
                  <Cell>{students?.content.find((s) => s.id === f.studentId)?.fullName ?? f.studentId.slice(0, 8)}</Cell>
                  <Cell>{f.dueDate}</Cell>
                  <Cell>{formatCurrency(f.amountDue)}</Cell>
                  <Cell className="font-medium">{formatCurrency(f.amountDue - f.amountPaid)}</Cell>
                </Row>
              ))}
            </Ledger>
          ) : (
            <EmptyState
              title="No pending fees"
              description="Every fee on record is fully paid."
              action={
                <Link to="/app/fees">
                  <Button variant="secondary">Go to fees</Button>
                </Link>
              }
            />
          )}
        </>
      )}
    </Layout>
  )
}
