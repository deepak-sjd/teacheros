import { useState, type FormEvent } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, ErrorBanner, Input } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { GenerateResult } from '@/lib/types'

/**
 * Notifications in this app are staff-triggered, not a background job (see
 * NotificationService javadoc on the backend) - a teacher taps a button
 * here when they actually want to alert parents, rather than the system
 * silently deciding on its own. Each action is deduped server-side for 3
 * days, so tapping twice in a row won't double-alert anyone.
 */
export function AlertsPage() {
  return (
    <Layout>
      <PageHeader title="Alerts" description="Send attendance and fee alerts to parents." />
      <div className="grid gap-4 max-w-xl">
        <LowAttendanceCard />
        <FeeReminderCard />
      </div>
    </Layout>
  )
}

function LowAttendanceCard() {
  const [threshold, setThreshold] = useState('75')
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState<GenerateResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setResult(null)
    setSending(true)
    try {
      const res = await api.post<GenerateResult>('/api/v1/notifications/generate/low-attendance', {
        thresholdPercentage: Number(threshold),
      })
      setResult(res)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not generate alerts.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="rounded-lg border border-line bg-white p-5">
      <h3 className="font-display text-lg text-ink">Low attendance</h3>
      <p className="text-sm text-slate mt-1">
        Alerts the guardian of every active student whose attendance is below the threshold.
      </p>
      {error && <ErrorBanner message={error} />}
      {result && (
        <p className="text-sm text-success mt-3">
          {result.created === 0 ? 'No new alerts needed.' : `Sent ${result.created} alert${result.created === 1 ? '' : 's'}.`}
        </p>
      )}
      <form onSubmit={handleSubmit} className="flex items-end gap-3 mt-4">
        <div className="w-32">
          <Input
            label="Threshold %"
            type="number"
            min={0}
            max={100}
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            required
          />
        </div>
        <Button type="submit" disabled={sending}>
          {sending ? 'Sending…' : 'Send alerts'}
        </Button>
      </form>
    </div>
  )
}

function FeeReminderCard() {
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState<GenerateResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSend() {
    setError(null)
    setResult(null)
    setSending(true)
    try {
      const res = await api.post<GenerateResult>('/api/v1/notifications/generate/fee-reminders')
      setResult(res)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not generate reminders.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="rounded-lg border border-line bg-white p-5">
      <h3 className="font-display text-lg text-ink">Fee reminders</h3>
      <p className="text-sm text-slate mt-1">
        Alerts the guardian of every student with a pending, partially paid, or overdue fee.
      </p>
      {error && <ErrorBanner message={error} />}
      {result && (
        <p className="text-sm text-success mt-3">
          {result.created === 0 ? 'No new reminders needed.' : `Sent ${result.created} reminder${result.created === 1 ? '' : 's'}.`}
        </p>
      )}
      <div className="mt-4">
        <Button onClick={handleSend} disabled={sending}>
          {sending ? 'Sending…' : 'Send fee reminders'}
        </Button>
      </div>
    </div>
  )
}
