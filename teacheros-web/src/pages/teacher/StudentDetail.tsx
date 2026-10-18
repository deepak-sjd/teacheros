import { useParams, Link } from 'react-router-dom'
import { Layout } from '@/components/Layout'
import { StudentProfile } from '@/components/StudentProfile'
import { StudentRecordTabs } from '@/components/StudentRecordTabs'
import { useAuth } from '@/lib/auth'

// Same four roles the backend allows to edit a profile / manage documents.
const EDIT_ROLES = ['OWNER', 'ADMIN', 'TEACHER', 'STAFF']

export function StudentDetail() {
  const { id } = useParams<{ id: string }>()
  const { role } = useAuth()
  if (!id) return null

  return (
    <Layout>
      <Link to="/app/students" className="text-sm text-slate hover:text-ink underline underline-offset-2 mb-6 inline-block">
        ← All students
      </Link>
      <StudentRecordTabs
        key={id}
        studentId={id}
        canEdit={role !== null && EDIT_ROLES.includes(role)}
        overview={<StudentProfile studentId={id} />}
      />
    </Layout>
  )
}
