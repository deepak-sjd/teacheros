import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { FeeLedgerLine } from '@/lib/types'
import { Button, Cell, EmptyState, ErrorBanner, Ledger, LedgerSkeleton, Row, StatusPill } from '@/components/ui'
import { formatCurrency, formatDate } from '@/lib/format'

/**
 * A student's fees with the discount shown as its own line under each fee.
 * Drop <FeeLedgerBreakdown studentId={...} /> into StudentProfile / MyFees;
 * pass canWaive for accountant-side views to show "Waive" on late fees.
 */
export function FeeLedgerBreakdown({ studentId, canWaive = false }: { studentId: string; canWaive?: boolean }) {
  const [lines, setLines] = useState<FeeLedgerLine[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  function load() {
    setLines(null)
    api
      .get<FeeLedgerLine[]>(`/api/v1/fee-ledger/students/${studentId}`)
      .then(setLines)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the fee ledger.'))
  }

  useEffect(load, [studentId])

  async function waive(line: FeeLedgerLine) {
    const reason = window.prompt('Reason for waiving this late fee?')
    if (!reason?.trim()) return
    setError(null)
    try {
      await api.post('/api/v1/fee-discounts/late-fee-waivers', { feeId: line.feeId, reason: reason.trim() })
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not waive this late fee.')
    }
  }

  if (error) return <ErrorBanner message={error} />
  if (lines === null) return <LedgerSkeleton columns={6} />
  if (lines.length === 0) return <EmptyState title="No fees on record" />

  return (
    <Ledger columns={['Fee', 'Due date', 'Amount', 'Paid', 'Status', '']}>
      {lines.flatMap((l) => {
        const rows = [
          <Row key={l.feeId}>
            <Cell className="font-medium">{l.description}</Cell>
            <Cell>{formatDate(l.dueDate)}</Cell>
            <Cell>{formatCurrency(l.grossAmount)}</Cell>
            <Cell>{formatCurrency(l.amountPaid)}</Cell>
            <Cell>
              <StatusPill status={l.status} />
            </Cell>
            <Cell>
              {canWaive && l.lineType === 'LATE_FEE' && l.status !== 'PAID' && (
                <Button variant="secondary" onClick={() => waive(l)}>
                  Waive
                </Button>
              )}
            </Cell>
          </Row>,
        ]
        if (l.discountAmount > 0) {
          rows.push(
            <Row key={`${l.feeId}-disc`}>
              <Cell className="text-success pl-8">Discount / concession</Cell>
              <Cell>{''}</Cell>
              <Cell className="text-success">− {formatCurrency(l.discountAmount)}</Cell>
              <Cell className="font-medium">Net {formatCurrency(l.amountDue)}</Cell>
              <Cell>{''}</Cell>
              <Cell>{''}</Cell>
            </Row>,
          )
        }
        return rows
      })}
    </Ledger>
  )
}
