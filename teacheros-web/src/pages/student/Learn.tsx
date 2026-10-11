import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner, Skeleton } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type { EnrolledCourseSummary } from '@/lib/types'

/** STUDENT only - the courses linked to the student's own batch. */
export function StudentLearn() {
  const [courses, setCourses] = useState<EnrolledCourseSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<EnrolledCourseSummary[]>('/api/v1/learn/courses')
      .then(setCourses)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your courses.'))
  }, [])

  return (
    <Layout>
      <PageHeader title="My courses" description="Video lessons, notes and links from your teacher." />
      {error && <ErrorBanner message={error} />}

      {courses === null ? (
        !error && (
          <div className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-20 rounded-lg" />
            ))}
          </div>
        )
      ) : courses.length === 0 ? (
        <EmptyState title="No courses yet" description="Courses linked to your batch will show up here." />
      ) : (
        <div className="space-y-2">
          {courses.map((c) => {
            const pct = c.totalLessons > 0 ? Math.round((c.completedLessons / c.totalLessons) * 100) : 0
            return (
              <Link
                key={c.courseId}
                to={`/app/learn/${c.courseId}`}
                className="block border border-line rounded-lg bg-white p-4 hover:bg-paper-dim/60 transition-colors"
              >
                <p className="font-medium text-ink">{c.title}</p>
                {c.description && <p className="text-sm text-slate mt-1 line-clamp-2">{c.description}</p>}
                <div className="h-2 rounded-full bg-paper-dim overflow-hidden mt-3">
                  <div className="h-full bg-chalk" style={{ width: `${pct}%` }} />
                </div>
                <p className="text-xs text-slate-light mt-1">
                  {c.completedLessons} of {c.totalLessons} lesson{c.totalLessons === 1 ? '' : 's'} done
                </p>
              </Link>
            )
          })}
        </div>
      )}
    </Layout>
  )
}
