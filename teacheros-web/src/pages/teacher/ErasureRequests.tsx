import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ErasureRequestResponse, ErasureStatus, Page } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Ledger, LedgerSkeleton, Row, Select, StatusPill } from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'
import { useAuth } from '@/lib/auth'
import { formatDate } from '@/lib/format'

export function ErasureRequestsPage() {
  const { role } = useAuth()
  const isOwner = role === 'OWNER'
  const [data, setData] = useState<Page<ErasureRequestResponse> | null>(null)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<ErasureStatus | ''>('PENDING')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  function load() {
    setError(null)
    api
      .get<Page<ErasureRequestResponse>>('/api/v1/erasure-requests', { page, size: PAGE_SIZE, status: status || undefined })
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load requests.'))
  }

  useEffect(load, [page, status])

  async function decide(r: ErasureRequestResponse, action: 'approve' | 'reject') {
    const msg =
      action === 'approve'
        ? `Approve erasure for ${r.studentName}? Their personal details, documents and messages are permanently deleted. This cannot be undone.`
        : `Reject this erasure request for ${r.studentName}?`
    if (!confirm(msg)) return
    setBusy(r.id)
    setError(null)
    try {
      await api.post(`/api/v1/erasure-requests/${r.id}/${action}`, {})
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not complete that action.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <Layout>
      <PageHeader
        title="Erasure requests"
        description="Guardians' requests to erase a child's data. Only the school owner can approve."
      />
      <div className="w-48 mb-6">
        <Select label="Status" value={status} onChange={(e) => { setStatus(e.target.value as ErasureStatus | ''); setPage(0) }}>
          <option value="">All</option>
          <option value="PENDING">Pending</option>
          <option value="FAILED">Failed</option>
          <option value="COMPLETED">Completed</option>
          <option value="REJECTED">Rejected</option>
        </Select>
      </div>
      {error && <ErrorBanner message={error} />}
      {!data ? (
        <LedgerSkeleton columns={5} />
      ) : data.totalElements === 0 ? (
        <EmptyState title="No requests" description="Nothing matches this filter." />
      ) : (
        <>
          <Ledger columns={['Student', 'Requested', 'By', 'Status', '']}>
            {data.content.map((r) => (
              <Row key={r.id}>
                <Cell className="font-medium">
                  {r.studentName}
                  {r.reason && <div className="text-xs text-slate font-normal">{r.reason}</div>}
                  {r.failureReason && <div className="text-xs text-danger font-normal">{r.failureReason}</div>}
                </Cell>
                <Cell>{formatDate(r.createdAt)}</Cell>
                <Cell>{r.requestedByRole.toLowerCase()}</Cell>
                <Cell>
                  <StatusPill status={r.status} />
                </Cell>
                <Cell>
                  {isOwner && (r.status === 'PENDING' || r.status === 'FAILED') && (
                    <div className="flex gap-2 whitespace-nowrap">
                      <Button variant="danger" disabled={busy !== null} onClick={() => decide(r, 'approve')}>
                        {r.status === 'FAILED' ? 'Retry' : 'Approve'}
                      </Button>
                      {r.status === 'PENDING' && (
                        <Button variant="secondary" disabled={busy !== null} onClick={() => decide(r, 'reject')}>
                          Reject
                        </Button>
                      )}
                    </div>
                  )}
                </Cell>
              </Row>
            ))}
          </Ledger>
          <Pagination page={data.number} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size} onChange={setPage} />
        </>
      )}
    </Layout>
  )
}
