import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { Avatar } from '@/components/ui'
import { ThemeToggle } from '@/components/ThemeToggle'

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
    { to: '/app/analytics', label: 'Analytics' },
    { to: '/app/reports', label: 'Reports' },
    { to: '/app/settings', label: 'Settings' },
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
    { to: '/app/analytics', label: 'Analytics' },
    { to: '/app/reports', label: 'Reports' },
    { to: '/app/settings', label: 'Settings' },
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
    { to: '/app/analytics', label: 'Analytics' },
    { to: '/app/reports', label: 'Reports' },
    { to: '/app/settings', label: 'Settings' },
  ],
  STAFF: [
    { to: '/app', label: 'Overview' },
    { to: '/app/students', label: 'Students' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
    { to: '/app/analytics', label: 'Analytics' },
    { to: '/app/reports', label: 'Reports' },
    { to: '/app/settings', label: 'Settings' },
  ],
  PARENT: [
    { to: '/app', label: 'Overview' },
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests & Marks' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
    { to: '/app/doubts', label: 'Ask Doubt' },
    { to: '/app/messages', label: 'Messages' },
    { to: '/app/settings', label: 'Settings' },
  ],
  STUDENT: [
    { to: '/app', label: 'Overview' },
    { to: '/app/checkin', label: 'Check In' },
    { to: '/app/timetable', label: 'Timetable' },
    { to: '/app/homework', label: 'Homework' },
    { to: '/app/study-material', label: 'Study Material' },
    { to: '/app/tests', label: 'Tests & Marks' },
    { to: '/app/take-test', label: 'Take Test' },
    { to: '/app/fees', label: 'Fees' },
    { to: '/app/alerts', label: 'Alerts' },
    { to: '/app/doubts', label: 'Ask Doubt' },
    { to: '/app/messages', label: 'Messages' },
    { to: '/app/settings', label: 'Settings' },
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

// 24x24 stroke icons (Lucide-style), keyed by route.
const ICONS: Record<string, string> = {
  '/app': 'M3 3h7v9H3z M14 3h7v5h-7z M14 12h7v9h-7z M3 16h7v5H3z',
  '/app/batches': 'M12 2 2 7l10 5 10-5-10-5z M2 17l10 5 10-5 M2 12l10 5 10-5',
  '/app/students':
    'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
  '/app/attendance': 'M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4 12 14.01l-3-3',
  '/app/homework': 'M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z',
  '/app/timetable':
    'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  '/app/study-material': 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z',
  '/app/tests':
    'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2 M9 2h6v4H9z M9 12h6 M9 16h4',
  '/app/fees': 'M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M1 10h22',
  '/app/alerts': 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0',
  '/app/analytics': 'M18 20V10 M12 20V4 M6 20v-6',
  '/app/reports': 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8',
  '/app/settings': 'M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6',
  '/app/checkin':
    'M3 7V5a2 2 0 0 1 2-2h2 M17 3h2a2 2 0 0 1 2 2v2 M21 17v2a2 2 0 0 1-2 2h-2 M7 21H5a2 2 0 0 1-2-2v-2 M7 12h10',
  '/app/take-test': 'M12 20h9 M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4z',
  '/app/doubts':
    'M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8z',
  '/app/messages': 'M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M22 6l-10 7L2 6',
}
const LOGOUT_ICON = 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9'

function Icon({ d, className = 'h-[18px] w-[18px]' }: { d: string; className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={`${className} shrink-0`}
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d={d} />
    </svg>
  )
}

const SIDEBAR_BG = 'bg-linear-to-b from-navy-deep to-navy text-white'
const MENU_ANIMATION_MS = 180

export function Layout({ children }: { children: ReactNode }) {
  const { role, phoneNumber, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const items = NAV_BY_ROLE[role ?? ''] ?? []
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuRendered, setMenuRendered] = useState(false)
  const [menuClosing, setMenuClosing] = useState(false)

  useEffect(() => {
    if (menuOpen) {
      setMenuRendered(true)
      setMenuClosing(false)
    } else if (menuRendered) {
      setMenuClosing(true)
      const timer = setTimeout(() => setMenuRendered(false), MENU_ANIMATION_MS)
      return () => clearTimeout(timer)
    }
  }, [menuOpen]) // eslint-disable-line react-hooks/exhaustive-deps

  const sidebarContent = (
    <>
      <div className="px-5 py-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-xl bg-linear-to-br from-chalk to-chalk-deep text-navy font-display text-lg font-bold flex items-center justify-center shadow-lg shadow-chalk/20">
            T
          </div>
          <div>
            <div className="font-display text-xl leading-none tracking-tight">TeacherOS</div>
            <div className="text-[11px] text-white/50 mt-1">{ROLE_LABEL[role ?? ''] ?? ''} workspace</div>
          </div>
        </div>
        <button
          onClick={() => setMenuOpen(false)}
          className="md:hidden text-white/70 hover:text-white text-2xl leading-none px-1 transition-colors"
          aria-label="Close menu"
        >
          ×
        </button>
      </div>

      <nav className="flex-1 px-3 py-2 space-y-0.5 overflow-y-auto">
        {items.map((item, i) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/app'}
            onClick={() => setMenuOpen(false)}
            style={{ animationDelay: `${i * 25}ms` }}
            className={({ isActive }) =>
              `group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-all duration-150 animate-fade-in ${
                isActive ? 'bg-white/10 text-white font-medium' : 'text-white/70 hover:bg-white/5 hover:text-white'
              }`
            }
          >
            {({ isActive }) => (
              <>
                <span
                  aria-hidden
                  className={`absolute left-0 top-1/2 -translate-y-1/2 w-1 rounded-r-full bg-chalk transition-all duration-200 ${
                    isActive ? 'h-5 opacity-100' : 'h-0 opacity-0'
                  }`}
                />
                <span className={`transition-colors ${isActive ? 'text-chalk' : 'text-white/50 group-hover:text-chalk'}`}>
                  <Icon d={ICONS[item.to] ?? ICONS['/app']} />
                </span>
                <span>{item.label}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="px-3 py-4 border-t border-white/10">
        <div className="flex items-center gap-2 rounded-lg bg-white/5 px-3 py-2.5">
          <Avatar name={phoneNumber ?? '?'} size={34} />
          <div className="min-w-0 flex-1">
            <div className="text-sm truncate">{phoneNumber}</div>
            <div className="text-[11px] text-white/50">{ROLE_LABEL[role ?? ''] ?? ''}</div>
          </div>
          <ThemeToggle className="text-white/60 hover:text-chalk hover:bg-white/10" />
          <button
            onClick={() => {
              logout()
              navigate('/login')
            }}
            className="h-8 w-8 shrink-0 flex items-center justify-center text-white/60 hover:text-chalk transition-colors rounded-lg hover:bg-white/10"
            aria-label="Sign out"
            title="Sign out"
          >
            <Icon d={LOGOUT_ICON} />
          </button>
        </div>
      </div>
    </>
  )

  return (
    <div className="min-h-screen flex bg-paper">
      <div className="md:hidden fixed top-0 inset-x-0 h-14 bg-linear-to-r from-navy-deep to-navy text-white flex items-center justify-between px-4 z-30 shadow-md">
        <span className="font-display text-lg tracking-tight">TeacherOS</span>
        <div className="flex items-center gap-1">
          <ThemeToggle className="text-white/70 hover:text-chalk hover:bg-white/10" />
          <button onClick={() => setMenuOpen(true)} className="p-2 -mr-2 transition-transform active:scale-90" aria-label="Open menu">
            <span className="block w-6 h-0.5 bg-white mb-1.5 rounded-full" />
            <span className="block w-4 h-0.5 bg-chalk mb-1.5 rounded-full ml-auto" />
            <span className="block w-6 h-0.5 bg-white rounded-full" />
          </button>
        </div>
      </div>

      {menuRendered && (
        <div className="md:hidden fixed inset-0 z-40 flex">
          <div
            className={`w-64 shrink-0 ${SIDEBAR_BG} flex flex-col shadow-xl ${
              menuClosing ? 'animate-slide-out-left' : 'animate-slide-in-left'
            }`}
          >
            {sidebarContent}
          </div>
          <div
            className={`flex-1 bg-black/50 backdrop-blur-sm ${menuClosing ? 'animate-fade-out' : 'animate-fade-in'}`}
            onClick={() => setMenuOpen(false)}
          />
        </div>
      )}

      <aside className={`hidden md:flex w-64 shrink-0 ${SIDEBAR_BG} flex-col sticky top-0 h-screen border-r border-white/5`}>
        {sidebarContent}
      </aside>

      <main className="relative flex-1 min-w-0 overflow-x-hidden pt-14 md:pt-0">
        <div
          aria-hidden
          className="pointer-events-none absolute -top-32 right-0 h-96 w-96 rounded-full bg-chalk-soft blur-3xl opacity-70 animate-drift"
        />
        {/* Keyed by pathname so each route change remounts this div and
            replays the fade - a lightweight stand-in for a page transition. */}
        <div key={location.pathname} className="relative max-w-5xl mx-auto px-4 py-6 sm:px-6 md:px-8 md:py-10 animate-slide-up">
          {children}
        </div>
      </main>
    </div>
  )
}

export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-6 md:mb-8">
      <div>
        <span className="block h-1 w-10 rounded-full bg-chalk mb-3" />
        <h1 className="font-display text-2xl sm:text-3xl tracking-tight text-ink">{title}</h1>
        {description && <p className="text-slate mt-1.5 text-sm max-w-xl">{description}</p>}
      </div>
      {action}
    </div>
  )
}
