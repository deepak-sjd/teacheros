import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { Avatar } from '@/components/ui'
import { ThemeToggle } from '@/components/ThemeToggle'
import { navItemsForRole } from '@/lib/nav/registry'
import { DEFAULT_NAV_ICON } from '@/lib/nav/types'
import { useTenantBrand } from '@/lib/tenantBrand'

// Nav items and their icons are no longer declared here - each module
// registers its own in lib/nav/modules/*.ts (see lib/nav/registry.ts).

const ROLE_LABEL: Record<string, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  TEACHER: 'Teacher',
  STAFF: 'Staff',
  PARENT: 'Parent',
  STUDENT: 'Student',
  ACCOUNTANT: 'Accountant',
  COUNSELLOR: 'Counsellor',
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

/**
 * Tenant logo, falling back to the "T" badge when there's no logo, it's
 * still loading, or the image fails to load. Logo URLs are signed and
 * expire (see TenantBrandProvider), so we remember which URL failed - a
 * freshly refetched URL then gets another try instead of staying on "T".
 */
function BrandMark({ className = 'h-9 w-9', textClassName = 'text-lg' }: { className?: string; textClassName?: string }) {
  const { logoUrl } = useTenantBrand()
  const [failedUrl, setFailedUrl] = useState<string | null>(null)

  if (logoUrl && logoUrl !== failedUrl) {
    return (
      <img
        src={logoUrl}
        alt="School logo"
        onError={() => setFailedUrl(logoUrl)}
        className={`${className} rounded-xl object-cover bg-white shrink-0 shadow-lg shadow-chalk/20`}
      />
    )
  }
  return (
    <div
      className={`${className} rounded-xl bg-linear-to-br from-chalk to-chalk-deep text-navy font-display ${textClassName} font-bold flex items-center justify-center shrink-0 shadow-lg shadow-chalk/20`}
    >
      T
    </div>
  )
}

const SIDEBAR_BG = 'bg-linear-to-b from-navy-deep to-navy text-white'
const MENU_ANIMATION_MS = 180

export function Layout({ children }: { children: ReactNode }) {
  const { role, phoneNumber, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const items = navItemsForRole(role)
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
        <div className="flex items-center gap-3 min-w-0">
          <BrandMark />
          <div className="min-w-0">
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
                  <Icon d={item.icon ?? DEFAULT_NAV_ICON} />
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
        <span className="flex items-center gap-2 font-display text-lg tracking-tight">
          <BrandMark className="h-7 w-7" textClassName="text-sm" />
          TeacherOS
        </span>
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
