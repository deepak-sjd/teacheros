import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState, Ledger, Row, Cell, StatusPill, Button, LedgerSkeleton } from '@/components/ui'
import { PayFeeModal } from '@/components/PayFeeModal'
import { ReceiptsModal } from '@/components/ReceiptsModal'
import { api } from '@/lib/api'
import { formatCurrency, formatDate } from '@/lib/format'
import type { Fee, Student } from '@/lib/types'

function FeeLedger({ student }: { student: Student }) {
  const [fees, setFees] = useState<Fee[] | null>(null)
  const [payingFee, setPayingFee] = useState<Fee | null>(null)
  const [receiptFee, setReceiptFee] = useState<Fee | null>(null)

  function load() {
    setFees(null)
    api.get<Fee[]>(`/api/v1/fees/students/${student.id}`).then(setFees)
  }

  useEffect(load, [student.id])

  if (fees === null) return <LedgerSkeleton columns={6} />
  if (fees.length === 0) return <EmptyState title="No fees on record" />

  return (
    <>
      <Ledger columns={['Due date', 'Amount due', 'Amount paid', 'Balance', 'Status', '']}>
        {fees.map((f) => {
          const balance = f.amountDue - f.amountPaid
          return (
            <Row key={f.id}>
              <Cell>{formatDate(f.dueDate)}</Cell>
              <Cell>{formatCurrency(f.amountDue)}</Cell>
              <Cell>{formatCurrency(f.amountPaid)}</Cell>
              <Cell className="font-medium">{formatCurrency(balance)}</Cell>
              <Cell>
                <StatusPill status={f.status} />
              </Cell>
              <Cell>
                <div className="flex items-center gap-2 whitespace-nowrap">
                  {balance > 0 && (
                    <Button variant="secondary" onClick={() => setPayingFee(f)}>
                      Pay now
                    </Button>
                  )}
                  {f.amountPaid > 0 && (
                    <Button variant="ghost" onClick={() => setReceiptFee(f)}>
                      Receipts
                    </Button>
                  )}
                </div>
              </Cell>
            </Row>
          )
        })}
      </Ledger>

      <PayFeeModal
        open={payingFee !== null}
        fee={payingFee}
        onClose={() => setPayingFee(null)}
        onPaid={() => {
          setPayingFee(null)
          load()
        }}
      />
      <ReceiptsModal fee={receiptFee} onClose={() => setReceiptFee(null)} />
    </>
  )
}

export function MyFees() {
  return (
    <StudentSectionShell title="Fees" description="Fee history and outstanding balance.">
      {(student) => <FeeLedger student={student} />}
    </StudentSectionShell>
  )
}
