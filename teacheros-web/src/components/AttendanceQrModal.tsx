import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { Modal, Button, ErrorBanner } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { QrTokenResponse } from '@/lib/types'

/**
 * Shows a QR code students scan (via /app/checkin) to mark themselves
 * present. Tokens are short-lived (10 min server-side default), so this
 * auto-refreshes a little before expiry rather than leaving a dead code on
 * screen for a slow-moving line of students.
 */
export function AttendanceQrModal({
  open,
  onClose,
  batchId,
  sessionDate,
}: {
  open: boolean
  onClose: () => void
  batchId: string
  sessionDate: string
}) {
  const [dataUrl, setDataUrl] = useState<string | null>(null)
  const [expiresAt, setExpiresAt] = useState<Date | null>(null)
  const [error, setError] = useState<string | null>(null)
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  async function fetchToken() {
    setError(null)
    try {
      const res = await api.post<QrTokenResponse>('/api/v1/attendance/qr-token', { batchId, sessionDate })
      const png = await QRCode.toDataURL(res.token, { width: 320, margin: 1 })
      setDataUrl(png)
      const expiry = new Date(res.expiresAt)
      setExpiresAt(expiry)

      // Refresh ~30s before the token actually expires so there's never a
      // window where a student scans a code that's already dead.
      const refreshInMs = Math.max(expiry.getTime() - Date.now() - 30_000, 5_000)
      if (refreshTimer.current) clearTimeout(refreshTimer.current)
      refreshTimer.current = setTimeout(fetchToken, refreshInMs)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not generate a QR code.')
    }
  }

  useEffect(() => {
    if (!open) {
      if (refreshTimer.current) clearTimeout(refreshTimer.current)
      setDataUrl(null)
      return
    }
    fetchToken()
    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, batchId, sessionDate])

  return (
    <Modal open={open} onClose={onClose} title="Scan to mark present">
      {error && <ErrorBanner message={error} />}
      {dataUrl ? (
        <div className="flex flex-col items-center gap-3">
          <img src={dataUrl} alt="Attendance QR code" className="w-full max-w-[280px]" />
          <p className="text-sm text-slate text-center">
            Students open "Check In" in their app and scan this. Refreshes automatically before it expires.
          </p>
          {expiresAt && (
            <p className="text-xs text-slate-light">Valid until {expiresAt.toLocaleTimeString()}</p>
          )}
        </div>
      ) : (
        !error && <p className="text-slate text-sm text-center">Generating…</p>
      )}
      <div className="mt-5 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  )
}
