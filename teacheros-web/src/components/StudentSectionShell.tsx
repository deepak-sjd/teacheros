import type { ReactNode } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner } from '@/components/ui'
import { useLinkedStudents } from '@/lib/useLinkedStudents'
import type { Student } from '@/lib/types'

export function StudentSectionShell({
  title,
  description,
  children,
}: {
  title: string
  description?: string
  children: (student: Student) => ReactNode
}) {
  const { students, activeId, setActiveId, activeStudent, loading, error } = useLinkedStudents()

  return (
    <Layout>
      <PageHeader title={title} description={description} />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : error ? (
        <ErrorBanner message={error} />
      ) : students.length === 0 ? (
        <EmptyState
          title="No student record linked to this account"
          description="Your account is linked automatically using the phone number your teacher has on file. If that's wrong, ask your teacher to check the phone number on the student record."
        />
      ) : (
        <>
          {students.length > 1 && (
            <div className="flex gap-2 mb-6">
              {students.map((s) => (
                <button
                  key={s.id}
                  onClick={() => setActiveId(s.id)}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium border transition-colors ${
                    activeId === s.id
                      ? 'bg-ink text-paper border-ink'
                      : 'bg-white text-slate border-line hover:border-ink'
                  }`}
                >
                  {s.fullName}
                </button>
              ))}
            </div>
          )}
          {activeStudent && children(activeStudent)}
        </>
      )}
    </Layout>
  )
}
