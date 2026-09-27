import { type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'

export function Ledger({ columns, children }: { columns: string[]; children: ReactNode }) {
  return (
    <div className="border border-line rounded-lg overflow-x-auto bg-white">
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
  return <tr className="border-b border-line last:border-0 hover:bg-paper-dim/60 transition-colors">{children}</tr>
}

export function Cell({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <td className={`px-4 py-3 text-ink ${className}`}>{children}</td>
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="border border-dashed border-line rounded-lg py-14 text-center bg-white">
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
    ghost: 'text-slate hover:text-ink',
    danger: 'bg-danger text-white hover:opacity-90',
  }
  return (
    <button
      className={`rounded-md px-4 py-2 text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${styles[variant]} ${className}`}
      {...props}
    />
  )
}

export function Input({ label, className = '', ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  return (
    <label className="block">
      {label && <span className="block text-sm font-medium text-ink mb-1.5">{label}</span>}
      <input
        className={`w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink placeholder:text-slate-light focus:border-ink focus:ring-1 focus:ring-ink outline-none ${className}`}
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
        className={`w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink placeholder:text-slate-light focus:border-ink focus:ring-1 focus:ring-ink outline-none resize-y ${className}`}
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
        className={`w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink focus:border-ink focus:ring-1 focus:ring-ink outline-none ${className}`}
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
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${style}`}>
      {status.replace('_', ' ').toLowerCase()}
    </span>
  )
}

export function StatBlock({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="border border-line rounded-lg bg-white px-5 py-4">
      <div className="text-slate text-sm">{label}</div>
      <div className="font-display text-3xl text-ink mt-1">{value}</div>
      {sub && <div className="text-xs text-slate-light mt-1">{sub}</div>}
    </div>
  )
}

export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  if (!open) return null
  return (
    <div className="fixed inset-0 bg-ink/40 flex items-center justify-center z-50 px-4" onClick={onClose}>
      <div
        className="bg-white rounded-lg border border-line w-full max-w-md p-6 shadow-xl"
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
    <div className="rounded-md bg-danger-soft text-danger text-sm px-4 py-3 mb-4 border border-danger/20">{message}</div>
  )
}
