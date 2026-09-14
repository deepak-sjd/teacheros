import { type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { initials } from '@/lib/format'

interface NavItem {
  to: string
  label: string
}

const NAV_BY_ROLE: Record<string, NavItem[]> = {
  OWNER: [
    { to: '/app', label: 'Overview' },
    { to: '/app/batches', label: 'Batches' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/attendance', label: 'Attendance' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/fees', label: 'Fees' },
  ],
  ADMIN: [
    { to: '/app', label: 'Overview' },
    { to: '/app/batches', label: 'Batches' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/attendance', label: 'Attendance' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/fees', label: 'Fees' },
  ],
  TEACHER: [
    { to: '/app', label: 'Overview' },
    { to: '/app/batches', label: 'Batches' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/attendance', label: 'Attendance' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/fees', label: 'Fees' },
  ],
  STAFF: [
    { to: '/app', label: 'Overview' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/fees', label: 'Fees' },
  ],
  PARENT: [{ to: '/app', label: 'My Children' }],
  STUDENT: [
    { to: '/app', label: 'My Attendance' },
    { to: '/app/fees', label: 'My Fees' },
  ],
}

const ROLE_LABEL: Record<string, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  TEACHER: 'Teacher',
  STAFF: 'Staff',
  PARENT: 'Parent',
  STUDENT: 'Student',
}

export function Layout({ children }: { children: ReactNode }) {
  const { role, phoneNumber, logout } = useAuth()
  const navigate = useNavigate()
  const items = NAV_BY_ROLE[role ?? ''] ?? []

  return (
    <div className="min-h-screen flex bg-paper">
      <aside className="w-60 shrink-0 bg-ink text-paper flex flex-col">
        <div className="px-6 py-6 border-b border-white/10">
          <div className="font-display text-2xl tracking-tight">TeacherOS</div>
          <div className="text-xs text-slate-light mt-1">{ROLE_LABEL[role ?? ''] ?? ''} workspace</div>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/app'}
              className={({ isActive }) =>
                `block rounded-md px-3 py-2 text-sm transition-colors ${
                  isActive ? 'bg-chalk text-ink font-medium' : 'text-paper/80 hover:bg-white/10'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="px-3 py-4 border-t border-white/10">
          <div className="flex items-center gap-3 px-3 py-2">
            <div className="h-8 w-8 rounded-full bg-chalk text-ink flex items-center justify-center text-xs font-semibold shrink-0">
              {initials(phoneNumber ?? '?')}
            </div>
            <div className="min-w-0">
              <div className="text-sm truncate">{phoneNumber}</div>
              <button
                onClick={() => {
                  logout()
                  navigate('/login')
                }}
                className="text-xs text-slate-light hover:text-chalk transition-colors"
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      </aside>
      <main className="flex-1 min-w-0 overflow-y-auto">
        <div className="max-w-5xl mx-auto px-8 py-10">{children}</div>
      </main>
    </div>
  )
}

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 mb-8">
      <div>
        <h1 className="font-display text-3xl text-ink">{title}</h1>
        {description && <p className="text-slate mt-1 text-sm">{description}</p>}
      </div>
      {action}
    </div>
  )
}
