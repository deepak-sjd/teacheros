import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import { getPublicSchoolSlug } from '@/lib/publicTenant'
import type { PublicCourseSummary } from '@/lib/types'
import { Badge, EmptyState, Skeleton } from '@/components/ui'
import { formatCurrency } from '@/lib/format'
import { PublicShell } from '@/pages/public/PublicShell'

export function PublicCourses() {
  const slug = getPublicSchoolSlug()
  const [courses, setCourses] = useState<PublicCourseSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!slug) {
      setError('No school selected. Open the link your school shared with you.')
      return
    }
    api
      .get<PublicCourseSummary[]>(`/api/v1/public/schools/${encodeURIComponent(slug)}/courses`)
      .then(setCourses)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load courses.'))
  }, [slug])

  return (
    <PublicShell>
      {(school) => (
        <>
          <div className="max-w-2xl mb-10">
            <span className="block h-1 w-10 rounded-full bg-chalk mb-4" />
            <h1 className="font-display text-3xl sm:text-4xl tracking-tight">
              {school ? `Courses at ${school.name}` : 'Courses'}
            </h1>
            <p className="text-slate mt-3">Pick a course to see what&rsquo;s covered, watch a demo, and get in touch.</p>
          </div>

          {error ? (
            <EmptyState title="Courses unavailable" description={error} />
          ) : courses === null ? (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-48 rounded-xl" />
              ))}
            </div>
          ) : courses.length === 0 ? (
            <EmptyState title="No courses yet" description="This school hasn't published any courses. Check back soon." />
          ) : (
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {courses.map((c) => (
                <Link
                  key={c.id}
                  to={`/courses/${c.id}`}
                  className="group flex flex-col rounded-lg border border-line bg-white p-6 transition-transform duration-200 hover:-translate-y-1"
                >
                  <div className="flex items-start justify-between gap-3">
                    <h2 className="font-display text-xl leading-snug text-ink">{c.title}</h2>
                    {c.hasDemoVideo && <Badge tone="brand">Demo</Badge>}
                  </div>
                  <p className="text-sm text-slate mt-3 flex-1">
                    {c.shortDescription ?? 'Tap to see the full syllabus and details.'}
                  </p>
                  <div className="flex items-center justify-between mt-6 pt-4 border-t border-line/70">
                    <span className="font-display text-xl tabular-nums text-ink">
                      {c.price > 0 ? formatCurrency(c.price) : 'Free'}
                    </span>
                    <span className="text-sm font-medium text-chalk-deep transition-transform duration-200 group-hover:translate-x-1">
                      View details &rarr;
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </PublicShell>
  )
}
