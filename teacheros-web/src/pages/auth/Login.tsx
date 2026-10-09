import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { Button, ErrorBanner, Input } from '@/components/ui'
import { ApiError } from '@/lib/api'
import { ThemeToggle } from '@/components/ThemeToggle'

const HIGHLIGHTS = [
  'Attendance by hand or QR self check-in',
  'Fees collected online, reminders on autopilot',
  'Tests, marks and an AI doubt solver',
]

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
    <div className="min-h-screen grid lg:grid-cols-2 bg-paper">
      {/* Brand panel */}
      <div className="relative hidden lg:flex flex-col justify-between overflow-hidden bg-linear-to-br from-navy-deep via-navy to-navy-light text-white p-12">
        <div aria-hidden className="absolute inset-0 bg-dots" />
        <div aria-hidden className="absolute -top-24 -right-24 h-96 w-96 rounded-full bg-chalk/25 blur-3xl animate-drift" />
        <div aria-hidden className="absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-chalk/10 blur-3xl animate-drift" />

        <div className="relative flex items-center gap-3">
          <div className="h-10 w-10 rounded-xl bg-linear-to-br from-chalk to-chalk-deep text-navy font-display text-xl font-bold flex items-center justify-center shadow-lg shadow-chalk/20">
            T
          </div>
          <span className="font-display text-2xl tracking-tight">TeacherOS</span>
        </div>

        <div className="relative max-w-md">
          <h1 className="font-display text-4xl xl:text-5xl leading-[1.1]">Run your batches, attendance, and fees in one place.</h1>
          <ul className="mt-8 space-y-3">
            {HIGHLIGHTS.map((h, i) => (
              <li
                key={h}
                className="flex items-center gap-3 text-white/80 text-sm animate-slide-up"
                style={{ animationDelay: `${300 + i * 120}ms` }}
              >
                <span className="h-5 w-5 rounded-full bg-chalk/20 text-chalk flex items-center justify-center shrink-0">
                  <svg viewBox="0 0 24 24" className="h-3 w-3" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                </span>
                {h}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-white/40">&copy; {new Date().getFullYear()} TeacherOS</p>
      </div>

      {/* Form panel */}
      <div className="relative flex items-center justify-center px-4 py-12 overflow-hidden">
      <div className="absolute top-4 right-4 z-10">
    <ThemeToggle className="text-slate hover:text-ink border border-line bg-white shadow-sm" />
  </div>
        <div aria-hidden className="lg:hidden absolute -top-24 -right-24 h-72 w-72 rounded-full bg-chalk-soft blur-3xl opacity-80" />
        <div className="relative w-full max-w-sm">
          <div className="lg:hidden text-center mb-8">
            <div className="font-display text-4xl text-ink">TeacherOS</div>
            <p className="text-slate text-sm mt-2">Run your batches, attendance, and fees in one place.</p>
          </div>

          <div className="mb-6 hidden lg:block">
            <h2 className="font-display text-3xl text-ink">{step === 'phone' ? 'Welcome back' : 'Check your phone'}</h2>
            <p className="text-slate text-sm mt-1.5">
              {step === 'phone' ? 'Log in with your phone number - no password needed.' : 'We sent a 6-digit code to verify it\u2019s you.'}
            </p>
          </div>

          <div className="bg-white border border-line rounded-2xl p-7 shadow-xl">
            {error && <ErrorBanner message={error} />}

            {step === 'phone' ? (
              <form key="phone" onSubmit={handleRequestOtp} className="space-y-4 animate-slide-up">
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
              <form key="otp" onSubmit={handleVerify} className="space-y-4 animate-slide-up">
                <p className="text-sm text-slate">
                  Enter the code sent to <span className="text-ink font-medium">{phone}</span>
                </p>
                <Input
                  label="6-digit code"
                  inputMode="numeric"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  className="text-center text-lg tracking-[0.5em] font-medium"
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
                  <div className="space-y-3 border-t border-line pt-4 animate-slide-up">
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
    </div>
  )
}
