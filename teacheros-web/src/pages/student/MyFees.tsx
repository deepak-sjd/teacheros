import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState, Ledger, Row, Cell, StatusPill } from '@/components/ui'
import { api } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/format'
import type { Fee, Student } from '@/lib/types'

function FeeLedger({ student }: { student: Student }) {
  const [fees, setFees] = useState<Fee[] | null>(null)

  useEffect(() => {
    setFees(null)
    api.get<Fee[]>(`/api/v1/fees/students/${student.id}`).then(setFees)
  }, [student.id])

  if (fees === null) return <p className="text-slate text-sm">Loading…</p>
  if (fees.length === 0) return <EmptyState title="No fees on record" />

  return (
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
  )
}

export function MyFees() {
  return (
    <StudentSectionShell title="Fees" description="Fee history and outstanding balance.">
      {(student) => <FeeLedger student={student} />}
    </StudentSectionShell>
  )
}
