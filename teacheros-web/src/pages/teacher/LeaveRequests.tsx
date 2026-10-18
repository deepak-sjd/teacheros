import { useCallback, useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { PAGE_SIZE, Pagination } from '@/components/Pagination'
import { Button, Cell, EmptyState, ErrorBanner, Ledger, LedgerSkeleton, Modal, Row, Select, Textarea } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { formatDate, formatDateTime } from '@/lib/format'
import type { Batch, DecideLeaveRequest, LeaveRequest, Page } from '@/lib/types'

type Decision = DecideLeaveRequest['decision']

function dateRange(l: LeaveRequest): string {
  return l.fromDate === l.toDate ? formatDate(l.fromDate) : `${formatDate(l.fromDate)} \u2013 ${formatDate(l.toDate)}`
}

export function LeaveRequestsPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<LeaveRequest> | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [target, setTarget] = useState<{ request: LeaveRequest; decision: Decision } | null>(null)
  const [note, setNote] = useState('')
  const [deciding, setDeciding] = useState(false)
  const [decideError, setDecideError] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<Page<Batch>>('/api/v1/batches', { size: 100 })
      .then((res) => setBatches(res.content))
      .catch(() => setBatches([])) // the filter is optional - the list still works without it
  }, [])

  const load = useCallback(() => {
    setError(null)
    return api
      .get<Page<LeaveRequest>>('/api/v1/leave-requests/pending', { batchId: batchId || undefined, page, size: PAGE_SIZE })
      .then(setData)
      .catch((err) => {
        setData(null)
        setError(err instanceof ApiError ? err.message : 'Could not load leave requests.')
      })
  }, [batchId, page])

  useEffect(() => {
    load()
  }, [load])

  function openDecision(request: LeaveRequest, decision: Decision) {
    setTarget({ request, decision })
    setNote('')
    setDecideError(null)
  }

  async function confirmDecision() {
    if (!target) return
    if (target.decision === 'REJECTED' && note.trim() === '') {
      setDecideError('Add a short note explaining why the request is being rejected.')
      return
    }
    setDeciding(true)
    setDecideError(null)
    const body: DecideLeaveRequest = { decision: target.decision, note: note.trim() || undefined }
    try {
      await api.patch<LeaveRequest>(`/api/v1/leave-requests/${target.request.id}/decision`, body)
      setTarget(null)
      // If that was the last row on this page, step back so the list isn't blank.
      if (data && data.content.length === 1 && page > 0) setPage(page - 1)
      else await load()
    } catch (err) {
      setDecideError(err instanceof ApiError ? err.message : 'Could not save the decision.')
    } finally {
      setDeciding(false)
    }
  }

  const approving = target?.decision === 'APPROVED'

  return (
    <Layout>
      <PageHeader
        title="Leave requests"
        description="Pending requests from parents and students. Approving marks the covered classes as excused."
      />

      <div className="max-w-xs mb-6">
        <Select
          label="Batch"
          value={batchId}
          onChange={(e) => {
            setBatchId(e.target.value)
            setPage(0)
          }}
        >
          <option value="">All batches</option>
          {batches.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </Select>
      </div>

      {error && <ErrorBanner message={error} />}

      {data === null && !error ? (
        <LedgerSkeleton columns={5} />
      ) : data && data.content.length === 0 ? (
        <EmptyState title="Nothing waiting" description="There are no pending leave requests right now." />
      ) : (
        data && (
          <>
            <Ledger columns={['Student', 'Dates', 'Reason', 'Requested', '']}>
              {data.content.map((l) => (
                <Row key={l.id}>
                  <Cell className="font-medium">{l.studentName}</Cell>
                  <Cell>{dateRange(l)}</Cell>
                  <Cell className="whitespace-pre-wrap">{l.reason}</Cell>
                  <Cell>{formatDateTime(l.createdAt)}</Cell>
                  <Cell>
                    <div className="flex justify-end gap-2 whitespace-nowrap">
                      <Button variant="secondary" onClick={() => openDecision(l, 'APPROVED')}>
                        Approve
                      </Button>
                      <Button variant="ghost" onClick={() => openDecision(l, 'REJECTED')}>
                        Reject
                      </Button>
                    </div>
                  </Cell>
                </Row>
              ))}
            </Ledger>
            <Pagination page={data.number} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
          </>
        )
      )}

      <Modal open={target !== null} onClose={() => (deciding ? undefined : setTarget(null))} title={approving ? 'Approve leave' : 'Reject leave'}>
        {target && (
          <div className="space-y-4">
            <div className="text-sm">
              <p className="font-medium text-ink">{target.request.studentName}</p>
              <p className="text-slate">{dateRange(target.request)}</p>
            </div>
            {approving && (
              <p className="text-xs text-slate">
                Classes already recorded as absent in this range become excused. Classes marked present or late are left alone.
              </p>
            )}
            {decideError && <ErrorBanner message={decideError} />}
            <Textarea
              label={approving ? 'Note to the family (optional)' : 'Reason for rejecting'}
              value={note}
              maxLength={500}
              onChange={(e) => setNote(e.target.value)}
            />
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setTarget(null)} disabled={deciding}>
                Cancel
              </Button>
              <Button variant={approving ? 'primary' : 'danger'} onClick={confirmDecision} disabled={deciding}>
                {deciding ? 'Saving…' : approving ? 'Approve' : 'Reject'}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </Layout>
  )
}
