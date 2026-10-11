import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api'
import { getPublicSchoolSlug } from '@/lib/publicTenant'
import type { PublicSchool } from '@/lib/types'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Skeleton } from '@/components/ui'

/**
 * Chrome for the unauthenticated catalogue pages: school name/logo, theme
 * toggle, login link. Renders inside a <main> so the app's card-hover and
 * entrance-animation rules (index.css) apply here too. `children` receives
 * the school once loaded (null while loading or if the slug is unknown).
 */
export function PublicShell({ children }: { children: (school: PublicSchool | null) => ReactNode }) {
  const slug = getPublicSchoolSlug()
  const [school, setSchool] = useState<PublicSchool | null>(null)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    if (!slug) {
      setLoaded(true)
      return
    }
    api
      .get<PublicSchool>(`/api/v1/public/schools/${encodeURIComponent(slug)}`)
      .then(setSchool)
      .catch(() => setSchool(null))
      .finally(() => setLoaded(true))
  }, [slug])

  return (
    <div className="min-h-screen bg-paper text-ink flex flex-col">
      <header className="sticky top-0 z-30 border-b border-line bg-paper/90 backdrop-blur">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
          <Link to="/courses" className="flex items-center gap-3 min-w-0">
            {school?.logoUrl ? (
              <img src={school.logoUrl} alt="" className="h-9 w-9 rounded-lg object-cover border border-line" />
            ) : (
              <div className="h-9 w-9 rounded-lg bg-linear-to-br from-chalk to-chalk-deep text-navy font-display font-bold flex items-center justify-center shrink-0">
                {(school?.name ?? 'S').charAt(0).toUpperCase()}
              </div>
            )}
            {loaded ? (
              <span className="font-display text-lg tracking-tight truncate">{school?.name ?? 'Courses'}</span>
            ) : (
              <Skeleton className="h-5 w-32" />
            )}
          </Link>
          <div className="flex items-center gap-2 shrink-0">
            <ThemeToggle />
            <Link
              to="/login"
              className="rounded-lg bg-linear-to-b from-ink-light to-ink text-paper px-4 py-2 text-sm font-medium shadow-sm transition-all duration-150 hover:shadow-md hover:-translate-y-px active:scale-[0.97]"
            >
              Log in
            </Link>
          </div>
        </div>
      </header>

      <main className="relative flex-1 overflow-x-hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute -top-24 right-0 h-80 w-80 rounded-full bg-chalk-soft blur-3xl opacity-70 animate-drift"
        />
        <div className="relative max-w-6xl mx-auto px-4 sm:px-6 py-10 sm:py-14">{children(school)}</div>
      </main>

      <footer className="border-t border-line py-6 text-center text-xs text-slate-light">
        Powered by TeacherOS
      </footer>
    </div>
  )
}
