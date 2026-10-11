import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { SessionJoinsResponse } from '@/lib/types'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, Modal, Row, Skeleton } from '@/components/ui'
import { formatDateTime, todayIso } from '@/lib/format'

/**
 * Teacher view of who clicked "Join" for one timetable slot on one date.
 * Read-only - it is a log, and deliberately does not mark attendance.
 */
export function SlotJoinsModal({
  slot,
  onClose,
}: {
  slot: { id: string; subject: string | null } | null
  onClose: () => void
}) {
  const [date, setDate] = useState(todayIso())
  const [data, setData] = useState<SessionJoinsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!slot) return
    setData(null)
    setError(null)
    api
      .get<SessionJoinsResponse>(`/api/v1/timetable/slots/${slot.id}/joins`, { date })
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load who joined.'))
  }, [slot, date])

  return (
    <Modal open={slot !== null} onClose={onClose} title={`Who joined${slot?.subject ? `: ${slot.subject}` : ''}`}>
      {error && <ErrorBanner message={error} />}

      <div className="mb-4 max-w-[12rem]">
        <Input label="Session date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>

      {data === null ? (
        !error && <Skeleton className="h-24" />
      ) : data.joined.length === 0 ? (
        <EmptyState title="Nobody joined" description="No one clicked Join for this session on that date." />
      ) : (
        <>
          <p className="text-sm text-slate mb-3">
            {data.joined.length} of {data.rosterSize} student{data.rosterSize === 1 ? '' : 's'} joined.
          </p>
          <Ledger columns={['Student', 'Joined at']}>
            {data.joined.map((j) => (
              <Row key={j.studentId}>
                <Cell>{j.studentName}</Cell>
                <Cell>{formatDateTime(j.joinedAt)}</Cell>
              </Row>
            ))}
          </Ledger>
        </>
      )}

      <div className="mt-5 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
