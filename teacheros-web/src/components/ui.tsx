import {
  useEffect,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'

// Shared focus treatment for every interactive control - one visible,
// consistent ring instead of each element relying on (or fighting) the
// browser default outline. focus-visible so it only shows for keyboard/
// a11y navigation, not every mouse click.
const FOCUS_RING =
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-chalk focus-visible:ring-offset-2 focus-visible:ring-offset-paper'

export function Ledger({ columns, children }: { columns: string[]; children: ReactNode }) {
  return (
    <div className="border border-line rounded-lg overflow-x-auto bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-line bg-paper-dim">
            {columns.map((c, i) => (
              <th key={i} className="text-left font-medium text-slate px-4 py-3">
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
  return <tr className="border-b border-line last:border-0 hover:bg-paper-dim/60 transition-colors duration-150">{children}</tr>
}

export function Cell({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <td className={`px-4 py-3 text-ink ${className}`}>{children}</td>
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="border border-dashed border-line rounded-lg py-14 text-center bg-white animate-fade-in">
      <p className="text-ink font-medium">{title}</p>
      {description && <p className="text-slate text-sm mt-1">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Button({
  variant = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' }) {
  const styles = {
    primary: 'bg-ink text-paper hover:bg-ink-light',
    secondary: 'bg-white text-ink border border-line hover:bg-paper-dim',
    ghost: 'text-slate hover:text-ink hover:bg-paper-dim',
    danger: 'bg-danger text-white hover:opacity-90',
  }
  return (
    <button
      className={`rounded-md px-4 py-2 text-sm font-medium transition-all duration-150 active:scale-[0.97] disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 ${FOCUS_RING} ${styles[variant]} ${className}`}
      {...props}
    />
  )
}

export function Input({ label, className = '', ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  return (
    <label className="block">
      {label && <span className="block text-sm font-medium text-ink mb-1.5">{label}</span>}
      <input
        className={`w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink placeholder:text-slate-light transition-colors duration-150 focus:border-ink outline-none ${FOCUS_RING} ${className}`}
        {...props}
      />
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
      <textarea
        rows={3}
        className={`w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink placeholder:text-slate-light transition-colors duration-150 focus:border-ink outline-none resize-y ${FOCUS_RING} ${className}`}
        {...props}
      />
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
      <select
        className={`w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink transition-colors duration-150 focus:border-ink outline-none ${FOCUS_RING} ${className}`}
        {...props}
      >
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
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium transition-colors duration-150 ${style}`}>
      {status.replace('_', ' ').toLowerCase()}
    </span>
  )
}

export function StatBlock({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="border border-line rounded-lg bg-white px-5 py-4 shadow-sm transition-shadow duration-200 hover:shadow-md">
      <div className="text-slate text-sm">{label}</div>
      <div className="font-display text-3xl text-ink mt-1">{value}</div>
      {sub && <div className="text-xs text-slate-light mt-1">{sub}</div>}
    </div>
  )
}

/**
 * Modal enter/exit animation without an animation library: a closing
 * modal stays mounted for one extra tick (ANIMATION_MS) so the exit
 * keyframes can actually play, then unmounts. `open` still drives
 * everything from the caller's side - this just delays "unmount" by the
 * animation's own duration rather than needing the caller to manage a
 * second "visible" flag.
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

  if (!rendered) return null

  return (
    <div
      className={`fixed inset-0 bg-ink/40 flex items-center justify-center z-50 px-4 ${closing ? 'animate-fade-out' : 'animate-fade-in'}`}
      onClick={onClose}
    >
      <div
        className={`bg-white rounded-lg border border-line w-full max-w-md p-6 shadow-xl ${closing ? 'animate-scale-out' : 'animate-scale-in'}`}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="font-display text-xl text-ink mb-4">{title}</h2>
        {children}
      </div>
    </div>
  )
}

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="rounded-md bg-danger-soft text-danger text-sm px-4 py-3 mb-4 border border-danger/20 animate-slide-up">
      {message}
    </div>
  )
}

/**
 * Loading skeletons - a plain pulsing block (Tailwind's built-in
 * animate-pulse, no extra CSS needed) plus two compositions shaped like
 * the things pages most often load: a stat row and a table. Existing
 * pages that currently render "Loading…" text can swap to these
 * independently; this file doesn't change what any page shows, only adds
 * the pieces to do it with.
 */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-paper-dim ${className}`} />
}

export function StatBlockSkeleton() {
  return (
    <div className="border border-line rounded-lg bg-white px-5 py-4">
      <Skeleton className="h-4 w-20 mb-3" />
      <Skeleton className="h-8 w-16" />
    </div>
  )
}

export function LedgerSkeleton({ columns, rows = 4 }: { columns: number; rows?: number }) {
  return (
    <div className="border border-line rounded-lg overflow-hidden bg-white shadow-sm">
      <div className="border-b border-line bg-paper-dim px-4 py-3">
        <Skeleton className="h-3 w-24" />
      </div>
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-6 border-b border-line last:border-0 px-4 py-3.5">
          {Array.from({ length: columns }).map((_, c) => (
            <Skeleton key={c} className="h-4 flex-1" />
          ))}
        </div>
      ))}
    </div>
  )
}
