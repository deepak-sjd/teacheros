import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { Button, ErrorBanner, Input } from '@/components/ui'
import { ApiError } from '@/lib/api'

export function Login() {
  const { requestOtp, verifyOtp } = useAuth()
  const navigate = useNavigate()
  const [step, setStep] = useState<'phone' | 'otp'>('phone')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [tenantName, setTenantName] = useState('')
  const [isNewAccount, setIsNewAccount] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleRequestOtp(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await requestOtp(phone)
      setStep('otp')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send the code. Check the number and try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleVerify(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await verifyOtp(phone, code, displayName || undefined, tenantName || undefined)
      navigate('/app')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That code didn\u2019t work. Request a new one and try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-ink flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="font-display text-4xl text-paper">TeacherOS</div>
          <p className="text-slate-light text-sm mt-2">Run your batches, attendance, and fees in one place.</p>
        </div>
        <div className="bg-paper rounded-xl p-7 shadow-xl">
          {error && <ErrorBanner message={error} />}

          {step === 'phone' ? (
            <form onSubmit={handleRequestOtp} className="space-y-4">
              <Input
                label="Phone number"
                type="tel"
                placeholder="+91 98765 43210"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                required
              />
              <Button type="submit" disabled={loading} className="w-full">
                {loading ? 'Sending code\u2026' : 'Send code'}
              </Button>
            </form>
          ) : (
            <form onSubmit={handleVerify} className="space-y-4">
              <p className="text-sm text-slate">
                Enter the code sent to <span className="text-ink font-medium">{phone}</span>
              </p>
              <Input
                label="6-digit code"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
                autoFocus
              />

              <button
                type="button"
                onClick={() => setIsNewAccount((v) => !v)}
                className="text-xs text-slate hover:text-ink underline underline-offset-2"
              >
                {isNewAccount ? 'I already have an account' : 'First time here? Set up your account'}
              </button>

              {isNewAccount && (
                <div className="space-y-3 border-t border-line pt-4">
                  <Input
                    label="Your name"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="e.g. Priya Sharma"
                  />
                  <Input
                    label="Institute / class name"
                    value={tenantName}
                    onChange={(e) => setTenantName(e.target.value)}
                    placeholder="e.g. Sunrise Tuition Centre"
                  />
                </div>
              )}

              <Button type="submit" disabled={loading} className="w-full">
                {loading ? 'Verifying\u2026' : 'Verify and continue'}
              </Button>
              <button
                type="button"
                onClick={() => setStep('phone')}
                className="text-xs text-slate hover:text-ink block mx-auto"
              >
                Use a different number
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
