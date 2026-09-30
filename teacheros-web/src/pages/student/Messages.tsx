import { StudentSectionShell } from '@/components/StudentSectionShell'
import { MessageThread } from '@/components/MessageThread'
import { useAuth } from '@/lib/auth'

export function StudentMessages() {
  const { role } = useAuth()
  return (
    <StudentSectionShell
      title="Messages"
      description={
        role === 'STUDENT'
          ? "Messages between your parent and your teacher - you can read along, but only a parent can reply."
          : 'Message your child\'s teacher directly.'
      }
    >
      {(student) => <MessageThread studentId={student.id} />}
    </StudentSectionShell>
  )
}
