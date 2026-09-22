import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState } from '@/components/ui'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { Homework, Page, Student } from '@/lib/types'

function HomeworkList({ student }: { student: Student }) {
  const [items, setItems] = useState<Homework[] | null>(null)

  useEffect(() => {
    if (!student.batchId) {
      setItems([])
      return
    }
    setItems(null)
    api
      .get<Page<Homework>>('/api/v1/homework', { batchId: student.batchId, size: 50 })
      .then((page) => setItems(page.content))
  }, [student.batchId])

  if (items === null) return <p className="text-slate text-sm">Loading…</p>
  if (!student.batchId || items.length === 0) {
    return <EmptyState title="No homework assigned" description="Nothing due for this batch right now." />
  }

  const sorted = [...items].sort((a, b) => a.dueDate.localeCompare(b.dueDate))

  return (
    <div className="space-y-2">
      {sorted.map((h) => (
        <div key={h.id} className="border border-line rounded-lg bg-white p-4">
          <div className="flex items-start justify-between gap-4">
            <p className="font-medium text-ink">{h.title}</p>
            <span className="text-xs text-slate whitespace-nowrap">Due {formatDate(h.dueDate)}</span>
          </div>
          {h.description && <p className="text-sm text-slate mt-1">{h.description}</p>}
        </div>
      ))}
    </div>
  )
}

export function StudentHomework() {
  return (
    <StudentSectionShell title="Homework" description="Assignments and due dates for your batch.">
      {(student) => <HomeworkList student={student} />}
    </StudentSectionShell>
  )
}
