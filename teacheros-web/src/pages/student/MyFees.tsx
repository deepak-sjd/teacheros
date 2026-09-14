import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner, Ledger, Row, Cell, StatusPill } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { Fee, Student } from '@/lib/types'
import { formatCurrency, formatDate } from '@/lib/format'

export function MyFees() {
  const [fees, setFees] = useState<Fee[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<Student>('/api/v1/students/me')
      .then((student) => api.get<Fee[]>(`/api/v1/fees/students/${student.id}`))
      .then(setFees)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your fees.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Layout>
      <PageHeader title="My fees" description="Your fee history and outstanding balance." />
      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : error ? (
        <ErrorBanner message={error} />
      ) : !fees || fees.length === 0 ? (
        <EmptyState title="No fees on record" />
      ) : (
        <Ledger columns={['Due date', 'Amount due', 'Amount paid', 'Balance', 'Status']}>
          {fees.map((f) => (
            <Row key={f.id}>
              <Cell>{formatDate(f.dueDate)}</Cell>
              <Cell>{formatCurrency(f.amountDue)}</Cell>
              <Cell>{formatCurrency(f.amountPaid)}</Cell>
              <Cell className="font-medium">{formatCurrency(f.amountDue - f.amountPaid)}</Cell>
              <Cell>
                <StatusPill status={f.status} />
              </Cell>
            </Row>
          ))}
        </Ledger>
      )}
    </Layout>
  )
}
