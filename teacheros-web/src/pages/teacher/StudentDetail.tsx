import { useParams, Link } from 'react-router-dom'
import { Layout } from '@/components/Layout'
import { StudentProfile } from '@/components/StudentProfile'

export function StudentDetail() {
  const { id } = useParams<{ id: string }>()
  if (!id) return null

  return (
    <Layout>
      <Link to="/app/students" className="text-sm text-slate hover:text-ink underline underline-offset-2 mb-6 inline-block">
        ← All students
      </Link>
      <StudentProfile studentId={id} />
    </Layout>
  )
}
