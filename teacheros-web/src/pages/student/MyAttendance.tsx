import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner } from '@/components/ui'
import { StudentProfile } from '@/components/StudentProfile'
import { api, ApiError } from '@/lib/api'
import type { Student } from '@/lib/types'

export function MyAttendance() {
  const [student, setStudent] = useState<Student | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api
      .get<Student>('/api/v1/students/me')
      .then(setStudent)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your record.'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Layout>
      <PageHeader title="My attendance" />

      {loading ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : error ? (
        <ErrorBanner message={error} />
      ) : !student ? (
        <EmptyState
          title="No student record linked to this account"
          description="Your account is linked automatically using the phone number your teacher has on file for you. If that's wrong, ask your teacher to check the phone number on your student record."
        />
      ) : (
        <StudentProfile studentId={student.id} showStudentHeader={false} />
      )}
    </Layout>
  )
}
