import { StudentSectionShell } from '@/components/StudentSectionShell'
import { StudentRecordTabs } from '@/components/StudentRecordTabs'

// Parent / student view of the profile and documents. Read-only: the backend
// only lets staff edit, and returns 404 for any student who isn't this
// account's own child / own record.
export function StudentRecords() {
  return (
    <StudentSectionShell title="Profile & documents" description="Details and documents the school has on file.">
      {(student) => <StudentRecordTabs key={student.id} studentId={student.id} canEdit={false} />}
    </StudentSectionShell>
  )
}
