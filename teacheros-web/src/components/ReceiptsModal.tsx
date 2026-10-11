import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { Button, ErrorBanner, Modal } from '@/components/ui'
import { formatCurrency, formatDate } from '@/lib/format'
import type { Fee, FeePaymentRecord } from '@/lib/types'

/** Lists a fee's payments with a PDF receipt download for each. */
export function ReceiptsModal({ fee, onClose }: { fee: Fee | null; onClose: () => void }) {
  const [items, setItems] = useState<FeePaymentRecord[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  useEffect(() => {
    if (!fee) return
    setItems(null)
    setError(null)
    api
      .get<FeePaymentRecord[]>(`/api/v1/receipts/fees/${fee.id}`)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load receipts.'))
  }, [fee])

  async function handleDownload(p: FeePaymentRecord) {
    setError(null)
    setDownloadingId(p.id)
    try {
      await api.downloadFile(`/api/v1/receipts/payments/${p.id}/receipt.pdf`, `${p.receiptNumber}.pdf`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not download the receipt.')
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <Modal open={fee !== null} onClose={onClose} title="Receipts">
      {error && <ErrorBanner message={error} />}
      {items === null && !error ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : items !== null && items.length === 0 ? (
        <p className="text-slate text-sm">No payments recorded for this fee yet.</p>
      ) : (
        <div className="space-y-2">
          {(items ?? []).map((p) => (
            <div key={p.id} className="flex items-center justify-between gap-3 rounded-md border border-line p-3">
              <div>
                <p className="text-sm font-medium text-ink">
                  {p.receiptNumber} {'\u00b7'} {formatCurrency(p.amountPaid)}
                </p>
                <p className="text-xs text-slate">
                  {formatDate(p.paidAt)}
                  {p.method ? ` \u00b7 ${p.method}` : ''}
                </p>
              </div>
              <Button variant="secondary" onClick={() => handleDownload(p)} disabled={downloadingId === p.id}>
                {downloadingId === p.id ? 'Preparing…' : 'Download'}
              </Button>
            </div>
          ))}
        </div>
      )}
      <div className="flex justify-end pt-4">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
