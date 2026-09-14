import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner } from '@/components/ui'
import { StudentProfile } from '@/components/StudentProfile'
import { api, ApiError } from '@/lib/api'
import type { Student } from '@/lib/types'

export function MyChildren() {
  const [children, setChildren] = useState<Student[] | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<Student[]>('/api/v1/students/me/children')
      .then((students) => {
        setChildren(students)
        setActiveId(students[0]?.id ?? null)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your children.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Layout>
      <PageHeader
        title="My children"
        description="Attendance and fee status for children linked to your account."
      />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : error ? (
        <ErrorBanner message={error} />
      ) : !children || children.length === 0 ? (
        <EmptyState
          title="No children linked to this account"
          description="Your account is linked automatically using the guardian phone number your child's teacher has on file. If that's wrong, ask the teacher to check the phone number on your child's student record."
        />
      ) : (
        <>
          {children.length > 1 && (
            <div className="flex gap-2 mb-6">
              {children.map((child) => (
                <button
                  key={child.id}
                  onClick={() => setActiveId(child.id)}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium border transition-colors ${
                    activeId === child.id ? 'bg-ink text-paper border-ink' : 'bg-white text-slate border-line hover:border-ink'
                  }`}
                >
                  {child.fullName}
                </button>
              ))}
            </div>
          )}
          {activeId && <StudentProfile studentId={activeId} />}
        </>
      )}
    </Layout>
  )
}
