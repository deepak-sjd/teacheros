import { useEffect, useState, type ReactNode } from 'react'
import { api, ApiError } from '@/lib/api'
import type { ConsentStatusResponse } from '@/lib/types'
import { Button, ErrorBanner } from '@/components/ui'

/**
 * Wraps the parent's app. Until the guardian has been asked once for every
 * child, shows a consent screen instead. A child whose consent was later
 * WITHDRAWN does not block the app (see ConsentService#status).
 */
export function ConsentGate({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<ConsentStatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [agreed, setAgreed] = useState(false)
  const [saving, setSaving] = useState(false)

  function load() {
    setError(null)
    api
      .get<ConsentStatusResponse>('/api/v1/consent/status')
      .then(setStatus)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your consent status.'))
  }

  useEffect(load, [])

  async function handleAgree() {
    if (!status) return
    setSaving(true)
    setError(null)
    try {
      setStatus(await api.post<ConsentStatusResponse>('/api/v1/consent/grant', { textVersion: status.currentTextVersion }))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your consent.')
    } finally {
      setSaving(false)
    }
  }

  if (error && !status) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-paper px-4">
        <div className="max-w-md w-full">
          <ErrorBanner message={error} />
          <Button onClick={load}>Try again</Button>
        </div>
      </div>
    )
  }
  if (!status) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-paper">
        <p className="text-slate text-sm">Loading…</p>
      </div>
    )
  }
  if (!status.consentRequired) return <>{children}</>

  const pending = status.children.filter((c) => c.state === 'PENDING')

  return (
    <div className="min-h-screen flex items-center justify-center bg-paper px-4 py-8">
      <div className="max-w-lg w-full bg-white border border-line rounded-xl p-6 shadow-sm">
        <h1 className="font-display text-2xl text-ink mb-2">Your consent is needed</h1>
        <p className="text-sm text-slate mb-4">
          As a parent or guardian, please review how your child&rsquo;s information is used before continuing.
        </p>
        <div className="text-sm text-ink space-y-2 mb-4">
          <p>
            We store and use the following about <strong>{pending.map((c) => c.fullName).join(', ')}</strong>: name,
            class, contact numbers, attendance, test marks, fee records, messages with the school, and any documents the
            school uploads.
          </p>
          <p>
            It is used only to run their classes &mdash; attendance, results, fees, messages and learning help &mdash;
            and is not used for advertising or tracking. You can download your child&rsquo;s data, withdraw this consent,
            or ask for the data to be erased at any time from Settings.
          </p>
        </div>
        {error && <ErrorBanner message={error} />}
        <label className="flex items-start gap-2 text-sm text-ink mb-4 cursor-pointer">
          <input type="checkbox" className="mt-1" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
          <span>I am the parent or legal guardian and I consent to the use of my child&rsquo;s data as described.</span>
        </label>
        <Button onClick={handleAgree} disabled={!agreed || saving} className="w-full">
          {saving ? 'Saving…' : 'I agree and continue'}
        </Button>
      </div>
    </div>
  )
}
