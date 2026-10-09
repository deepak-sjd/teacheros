import {
  useEffect,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import { initials } from '@/lib/format'

// Keyboard-only focus ring for buttons.
const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-chalk focus-visible:ring-offset-2 focus-visible:ring-offset-paper'

// Shared look for every text-entry control.
const FIELD =
  'w-full rounded-lg border border-line bg-white px-3.5 py-2.5 text-sm text-ink shadow-sm placeholder:text-slate-light transition-all duration-150 hover:border-slate-light/60 focus:border-chalk-deep focus:ring-4 focus:ring-chalk/25 outline-none disabled:bg-paper-dim disabled:text-slate-light'

export function Ledger({ columns, children }: { columns: string[]; children: ReactNode }) {
  return (
    <div className="border border-line rounded-xl overflow-x-auto bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-line bg-paper-dim/70">
            {columns.map((c, i) => (
              <th key={i} className="text-left text-[11px] font-semibold uppercase tracking-wider text-slate-light px-4 py-3">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

export function Row({ children }: { children: ReactNode }) {
  return (
    <tr className="border-b border-line/70 last:border-0 transition-colors duration-150 hover:bg-chalk-soft/40">
      {children}
    </tr>
  )
}

export function Cell({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <td className={`px-4 py-3.5 text-ink ${className}`}>{children}</td>
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="border border-dashed border-line rounded-xl py-14 px-6 text-center bg-linear-to-b from-white to-paper-dim/40 animate-fade-in">
      <div className="mx-auto mb-4 h-14 w-14 rounded-2xl bg-chalk-soft text-chalk-deep flex items-center justify-center animate-float">
        <svg viewBox="0 0 24 24" className="h-7 w-7" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M22 12h-6l-2 3h-4l-2-3H2 M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
        </svg>
      </div>
      <p className="text-ink font-medium">{title}</p>
      {description && <p className="text-slate text-sm mt-1 max-w-sm mx-auto">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' }) {
  const styles = {
    primary:
      'bg-linear-to-b from-ink-light to-ink text-paper shadow-sm hover:shadow-md hover:-translate-y-px hover:from-ink hover:to-ink-deep',
    secondary: 'bg-white text-ink border border-line shadow-sm hover:border-ink/30 hover:bg-paper-dim hover:-translate-y-px',
    ghost: 'text-slate hover:text-ink hover:bg-paper-dim',
    danger: 'bg-danger text-white shadow-sm hover:opacity-90 hover:-translate-y-px',
  }
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-all duration-150 active:scale-[0.97] active:translate-y-0 disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0 disabled:active:scale-100 ${FOCUS_RING} ${styles[variant]} ${className}`}
      {...props}
    />
  )
}

export function Input({ label, className = '', ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  return (
    <label className="block">
      {label && <span className="block text-sm font-medium text-ink mb-1.5">{label}</span>}
      <input className={`${FIELD} ${className}`} {...props} />
    </label>
  )
}

export function Textarea({
  label,
  className = '',
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }) {
  return (
    <label className="block">
      {label && <span className="block text-sm font-medium text-ink mb-1.5">{label}</span>}
      <textarea rows={3} className={`${FIELD} resize-y ${className}`} {...props} />
    </label>
  )
}

export function Select({
  label,
  className = '',
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { label?: string }) {
  return (
    <label className="block">
      {label && <span className="block text-sm font-medium text-ink mb-1.5">{label}</span>}
      <select className={`${FIELD} ${className}`} {...props}>
        {children}
      </select>
    </label>
  )
}

const STATUS_STYLES: Record<string, string> = {
  PRESENT: 'bg-success-soft text-success',
  PAID: 'bg-success-soft text-success',
  ABSENT: 'bg-danger-soft text-danger',
  OVERDUE: 'bg-danger-soft text-danger',
  LATE: 'bg-warn-soft text-warn',
  EXCUSED: 'bg-paper-dim text-slate',
  PENDING: 'bg-warn-soft text-warn',
  PARTIALLY_PAID: 'bg-warn-soft text-warn',
}

export function StatusPill({ status }: { status: string }) {
  const style = STATUS_STYLES[status] ?? 'bg-paper-dim text-slate'
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium capitalize transition-colors duration-150 ${style}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {status.replace(/_/g, ' ').toLowerCase()}
    </span>
  )
}

/**
 * Animates the first number found in a display string (e.g. "₹12,400",
 * "75%", "3 of 5") from 0 up to its value, keeping the prefix/suffix and
 * digit grouping. Strings with no number ("—", "Not yet ranked") render
 * as-is. Skips the animation entirely under prefers-reduced-motion.
 */
export function CountUp({ value, duration = 800 }: { value: string; duration?: number }) {
  const match = /^([^\d]*)(\d[\d,]*)(\.\d+)?(.*)$/s.exec(value)
  const target = match ? Number((match[2] + (match[3] ?? '')).replace(/,/g, '')) : null
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    if (target === null) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setProgress(1)
      return
    }
    let raf = 0
    const start = performance.now()
    const tick = (now: number) => {
      const t = Math.min((now - start) / duration, 1)
      setProgress(1 - Math.pow(1 - t, 3)) // ease-out cubic
      if (t < 1) raf = requestAnimationFrame(tick)
    }
    setProgress(0)
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!match || target === null) return <>{value}</>

  const decimals = match[3] ? match[3].length - 1 : 0
  const current = target * progress
  const formatted = match[2].includes(',')
    ? current.toLocaleString('en-IN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    : current.toFixed(decimals)

  return <>{`${match[1]}${formatted}${match[4]}`}</>
}

export function StatBlock({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="group relative overflow-hidden border border-line rounded-xl bg-white px-5 py-4 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <span
        aria-hidden
        className="absolute inset-y-0 left-0 w-1 bg-linear-to-b from-chalk to-chalk-deep opacity-60 transition-opacity duration-200 group-hover:opacity-100"
      />
      <div className="text-slate text-xs font-medium uppercase tracking-wide">{label}</div>
      <div className="font-display text-3xl text-ink mt-1.5 tabular-nums">
        <CountUp value={value} />
      </div>
      {sub && <div className="text-xs text-slate-light mt-1">{sub}</div>}
    </div>
  )
}

/** Generic surface for new screens: same look as the auto-upgraded cards. */
export function Card({
  children,
  className = '',
  interactive = false,
}: {
  children: ReactNode
  className?: string
  interactive?: boolean
}) {
  return (
    <div
      className={`border border-line rounded-xl bg-white p-5 shadow-sm transition-all duration-200 ${
        interactive ? 'hover:-translate-y-0.5 hover:shadow-md hover:border-chalk/50 cursor-pointer' : ''
      } ${className}`}
    >
      {children}
    </div>
  )
}

const BADGE_TONES = {
  neutral: 'bg-paper-dim text-slate',
  brand: 'bg-chalk-soft text-chalk-deep',
  success: 'bg-success-soft text-success',
  warn: 'bg-warn-soft text-warn',
  danger: 'bg-danger-soft text-danger',
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: keyof typeof BADGE_TONES }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${BADGE_TONES[tone]}`}>
      {children}
    </span>
  )
}

export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  return (
    <div
      className="rounded-full bg-linear-to-br from-chalk to-chalk-deep text-navy font-semibold flex items-center justify-center shrink-0 shadow-sm"
      style={{ height: size, width: size, fontSize: Math.max(size * 0.36, 10) }}
    >
      {initials(name)}
    </div>
  )
}

/**
 * Modal enter/exit animation without an animation library: a closing
 * modal stays mounted for one extra tick (ANIMATION_MS) so the exit
 * keyframes can actually play, then unmounts. `open` still drives
 * everything from the caller's side. Escape closes it.
 */
const MODAL_ANIMATION_MS = 150

export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const [rendered, setRendered] = useState(open)
  const [closing, setClosing] = useState(false)

  useEffect(() => {
    if (open) {
      setRendered(true)
      setClosing(false)
    } else if (rendered) {
      setClosing(true)
      const timer = setTimeout(() => setRendered(false), MODAL_ANIMATION_MS)
      return () => clearTimeout(timer)
    }
  }, [open]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!rendered) return null

  return (
    <div
      className={`fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 px-4 ${closing ? 'animate-fade-out' : 'animate-fade-in'}`}
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`bg-white rounded-2xl border border-line w-full max-w-md max-h-[90vh] overflow-y-auto p-6 shadow-xl ${closing ? 'animate-scale-out' : 'animate-scale-in'}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="h-1 w-10 rounded-full bg-chalk mb-4" />
        <h2 className="font-display text-xl text-ink mb-4">{title}</h2>
        {children}
      </div>
    </div>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2.5 rounded-lg bg-danger-soft text-danger text-sm px-4 py-3 mb-4 border border-danger/20 animate-slide-up">
      <span className="mt-1.5 h-1.5 w-1.5 rounded-full bg-danger shrink-0" />
      <span>{message}</span>
    </div>
  )
}

/** Loading skeletons - shimmering placeholder blocks shaped like stat rows and tables. */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`skeleton rounded-md ${className}`} />
}

export function StatBlockSkeleton() {
  return (
    <div className="border border-line rounded-xl bg-white px-5 py-4">
      <Skeleton className="h-3 w-20 mb-3" />
      <Skeleton className="h-8 w-16" />
    </div>
  )
}

export function LedgerSkeleton({ columns, rows = 4 }: { columns: number; rows?: number }) {
  return (
    <div className="border border-line rounded-xl overflow-hidden bg-white shadow-sm">
      <div className="border-b border-line bg-paper-dim/70 px-4 py-3">
        <Skeleton className="h-3 w-24" />
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-6 border-b border-line/70 last:border-0 px-4 py-3.5">
          {Array.from({ length: columns }).map((_, c) => (
            <Skeleton key={c} className="h-4 flex-1" />
          ))}
        </div>
      ))}
    </div>
  )
}
