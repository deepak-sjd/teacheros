import { useState, type ReactNode } from 'react'
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
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
  ],
  ADMIN: [
    { to: '/app', label: 'Overview' },
    { to: '/app/batches', label: 'Batches' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/attendance', label: 'Attendance' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
  ],
  TEACHER: [
    { to: '/app', label: 'Overview' },
    { to: '/app/batches', label: 'Batches' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/attendance', label: 'Attendance' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
  ],
  STAFF: [
    { to: '/app', label: 'Overview' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
  ],
  PARENT: [
    { to: '/app', label: 'Overview' },
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests & Marks' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
  ],
  STUDENT: [
    { to: '/app', label: 'Overview' },
    { to: '/app/checkin', label: 'Check In' },
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests & Marks' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
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
  const [menuOpen, setMenuOpen] = useState(false)

  const sidebarContent = (
    <>
      <div className="px-6 py-6 border-b border-white/10 flex items-center justify-between">
        <div>
          <div className="font-display text-2xl tracking-tight">TeacherOS</div>
          <div className="text-xs text-slate-light mt-1">{ROLE_LABEL[role ?? ''] ?? ''} workspace</div>
        </div>
        <button
          onClick={() => setMenuOpen(false)}
          className="md:hidden text-paper/70 hover:text-paper text-2xl leading-none px-1"
          aria-label="Close menu"
        >
          ×
        </button>
      </div>
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/app'}
            onClick={() => setMenuOpen(false)}
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
    </>
  )

  return (
    <div className="min-h-screen flex bg-paper">
      <div className="md:hidden fixed top-0 inset-x-0 h-14 bg-ink text-paper flex items-center justify-between px-4 z-30">
        <span className="font-display text-lg tracking-tight">TeacherOS</span>
        <button onClick={() => setMenuOpen(true)} className="p-2 -mr-2" aria-label="Open menu">
          <span className="block w-6 h-0.5 bg-paper mb-1.5" />
          <span className="block w-6 h-0.5 bg-paper mb-1.5" />
          <span className="block w-6 h-0.5 bg-paper" />
        </button>
      </div>

      {menuOpen && (
        <div className="md:hidden fixed inset-0 z-40 flex">
          <div className="w-64 shrink-0 bg-ink text-paper flex flex-col">{sidebarContent}</div>
          <div className="flex-1 bg-ink/40" onClick={() => setMenuOpen(false)} />
        </div>
      )}

      <aside className="hidden md:flex w-60 shrink-0 bg-ink text-paper flex-col">{sidebarContent}</aside>

      <main className="flex-1 min-w-0 overflow-y-auto pt-14 md:pt-0">
        <div className="max-w-5xl mx-auto px-4 py-6 sm:px-6 md:px-8 md:py-10">{children}</div>
      </main>
    </div>
  )
}

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-6 md:mb-8">
      <div>
        <h1 className="font-display text-2xl sm:text-3xl text-ink">{title}</h1>
        {description && <p className="text-slate mt-1 text-sm">{description}</p>}
      </div>
      {action}
    </div>
  )
}
