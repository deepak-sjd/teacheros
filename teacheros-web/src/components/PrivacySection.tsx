import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ConsentStatusResponse, ErasureRequestResponse } from '@/lib/types'
import { Button, ErrorBanner, Modal, Textarea } from '@/components/ui'
import { formatDate } from '@/lib/format'

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Waiting for the school owner to review',
  APPROVED: 'Approved - processing',
  COMPLETED: 'Completed - data erased',
  REJECTED: 'Rejected',
  FAILED: 'Approved, but processing failed - the school will retry',
}

export function PrivacySection() {
  const [consent, setConsent] = useState<ConsentStatusResponse | null>(null)
  const [erasures, setErasures] = useState<Record<string, ErasureRequestResponse[]>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [erasing, setErasing] = useState<{ id: string; name: string } | null>(null)
  const [reason, setReason] = useState('')

  async function load() {
    try {
      const c = await api.get<ConsentStatusResponse>('/api/v1/consent/status')
      setConsent(c)
      const entries = await Promise.all(
        c.children.map(async (ch) => [
          ch.studentId,
          await api.get<ErasureRequestResponse[]>(`/api/v1/students/${ch.studentId}/erasure-requests`),
        ] as const),
      )
      setErasures(Object.fromEntries(entries))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load privacy settings.')
    }
  }

  useEffect(() => {
    load()
  }, [])

  async function act(key: string, fn: () => Promise<unknown>) {
    setBusy(key)
    setError(null)
    try {
      await fn()
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That did not work. Please try again.')
    } finally {
      setBusy(null)
    }
  }

  function download(id: string, name: string) {
    return act(`dl-${id}`, () => api.downloadFile(`/api/v1/students/${id}/data-export`, `${name.replace(/\s+/g, '-')}-data.zip`))
  }

  async function submitErasure() {
    if (!erasing) return
    const target = erasing
    await act(`er-${target.id}`, () =>
      api.post(`/api/v1/students/${target.id}/erasure-requests`, { reason: reason || undefined }),
    )
    setErasing(null)
    setReason('')
  }

  if (!consent) return error ? <ErrorBanner message={error} /> : <p className="text-slate text-sm">Loading…</p>
  if (consent.children.length === 0) return null

  return (
    <section className="pt-10 border-t border-line">
      <h2 className="font-display text-xl text-ink mb-1">Your child&rsquo;s data and privacy</h2>
      <p className="text-sm text-slate mb-4">Download, withdraw consent for, or ask to erase your child&rsquo;s data.</p>
      {error && <ErrorBanner message={error} />}

      <div className="space-y-3">
        {consent.children.map((c) => {
          const latest = erasures[c.studentId]?.[0]
          const open = latest && ['PENDING', 'APPROVED', 'FAILED'].includes(latest.status)
          return (
            <div key={c.studentId} className="border border-line rounded-lg bg-white p-4">
              <p className="font-medium text-ink">{c.fullName}</p>
              <p className="text-xs text-slate mt-1">
                Consent:{' '}
                {c.state === 'GRANTED'
                  ? `given${c.grantedAt ? ' on ' + formatDate(c.grantedAt) : ''}`
                  : c.state === 'WITHDRAWN'
                    ? `withdrawn${c.withdrawnAt ? ' on ' + formatDate(c.withdrawnAt) : ''}`
                    : 'not given yet'}
              </p>
              {latest && (
                <p className="text-xs text-slate mt-1">
                  Erasure request: {STATUS_LABEL[latest.status] ?? latest.status} ({formatDate(latest.createdAt)})
                </p>
              )}
              <div className="flex flex-wrap gap-2 mt-3">
                <Button variant="secondary" disabled={busy !== null} onClick={() => download(c.studentId, c.fullName)}>
                  {busy === `dl-${c.studentId}` ? 'Preparing…' : 'Download data (ZIP)'}
                </Button>
                {c.state === 'GRANTED' && (
                  <Button
                    variant="secondary"
                    disabled={busy !== null}
                    onClick={() =>
                      confirm(`Withdraw consent for ${c.fullName}? The school will be told you no longer consent.`) &&
                      act(`wd-${c.studentId}`, () => api.post(`/api/v1/consent/students/${c.studentId}/withdraw`))
                    }
                  >
                    Withdraw consent
                  </Button>
                )}
                {c.state === 'WITHDRAWN' && (
                  <Button
                    variant="secondary"
                    disabled={busy !== null}
                    onClick={() =>
                      act(`gr-${c.studentId}`, () =>
                        api.post('/api/v1/consent/grant', { textVersion: consent.currentTextVersion, studentIds: [c.studentId] }),
                      )
                    }
                  >
                    Give consent again
                  </Button>
                )}
                <Button
                  variant="danger"
                  disabled={busy !== null || !!open || latest?.status === 'COMPLETED'}
                  onClick={() => setErasing({ id: c.studentId, name: c.fullName })}
                >
                  Request erasure
                </Button>
              </div>
            </div>
          )
        })}
      </div>

      <Modal open={erasing !== null} onClose={() => setErasing(null)} title="Request data erasure">
        <p className="text-sm text-slate mb-3">
          If approved by the school owner, {erasing?.name}&rsquo;s personal details, documents and messages are
          permanently deleted. Fee and result totals are kept for the school&rsquo;s accounts without any personal details.
          This cannot be undone.
        </p>
        <Textarea label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={1000} />
        <div className="flex gap-2 justify-end pt-3">
          <Button variant="secondary" onClick={() => setErasing(null)}>
            Cancel
          </Button>
          <Button variant="danger" onClick={submitErasure} disabled={busy !== null}>
            Send request
          </Button>
        </div>
      </Modal>
    </section>
  )
}
