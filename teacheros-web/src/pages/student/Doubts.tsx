import { StudentSectionShell } from '@/components/StudentSectionShell'
import { DoubtChat } from '@/components/DoubtChat'

export function StudentDoubts() {
  return (
    <StudentSectionShell
      title="Ask a Doubt"
      description="Get step-by-step help from the AI tutor on any schoolwork question."
    >
      {(student) => <DoubtChat studentId={student.id} />}
    </StudentSectionShell>
  )
}
