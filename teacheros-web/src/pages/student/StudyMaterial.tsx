import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState } from '@/components/ui'
import { api } from '@/lib/api'
import type { DownloadUrlResponse, Page, Student, StudyMaterial } from '@/lib/types'

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function handleDownload(id: string) {
  const { url } = await api.get<DownloadUrlResponse>(`/api/v1/study-material/${id}/download-url`)
  window.open(url, '_blank')
}

function MaterialList({ student }: { student: Student }) {
  const [items, setItems] = useState<StudyMaterial[] | null>(null)

  useEffect(() => {
    if (!student.batchId) {
      setItems([])
      return
    }
    setItems(null)
    api
      .get<Page<StudyMaterial>>('/api/v1/study-material', { batchId: student.batchId, size: 50 })
      .then((page) => setItems(page.content))
  }, [student.batchId])

  if (items === null) return <p className="text-slate text-sm">Loading…</p>
  if (!student.batchId || items.length === 0) {
    return <EmptyState title="No files yet" description="Nothing uploaded for this batch right now." />
  }

  return (
    <div className="space-y-2">
      {items.map((m) => (
        <div key={m.id} className="border border-line rounded-lg bg-white p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="font-medium text-ink">{m.title}</p>
              {m.description && <p className="text-sm text-slate mt-1">{m.description}</p>}
              <p className="text-xs text-slate mt-1">
                {m.originalFilename} {'\u00b7'} {formatFileSize(m.fileSizeBytes)}
              </p>
            </div>
            <button
              onClick={() => handleDownload(m.id)}
              className="text-sm text-slate hover:text-ink underline underline-offset-2 whitespace-nowrap"
            >
              Download
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}

export function StudentStudyMaterial() {
  return (
    <StudentSectionShell title="Study material" description="Notes, PDFs and recordings for your batch.">
      {(student) => <MaterialList student={student} />}
    </StudentSectionShell>
  )
}
