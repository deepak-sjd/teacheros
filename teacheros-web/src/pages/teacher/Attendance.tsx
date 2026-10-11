import { useEffect, useState } from 'react'
import { api, ApiError } from '@/lib/api'
import { fetchAllPages } from '@/lib/fetchAllPages'
import type { AttendanceStatus, Batch, Page, Student, StudentAttendanceRecord } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Row, Select } from '@/components/ui'
import { AttendanceQrModal } from '@/components/AttendanceQrModal'
import { todayIso } from '@/lib/format'

const STATUSES: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED']

export function Attendance() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [sessionDate, setSessionDate] = useState(todayIso())
  const [students, setStudents] = useState<Student[]>([])
  const [statuses, setStatuses] = useState<Record<string, AttendanceStatus>>({})
  const [existing, setExisting] = useState<StudentAttendanceRecord[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [qrOpen, setQrOpen] = useState(false)

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => {
      setBatches(res.content)
      if (res.content.length > 0) setBatchId(res.content[0].id)
    })
  }, [])

  useEffect(() => {
    if (!batchId) return
    // Guards against a slow response for a previously selected batch
    // landing after the person has already switched to another one.
    let cancelled = false
    setLoading(true)
    setSaved(false)
    setError(null)
    fetchAllPages<Student>('/api/v1/students', { batchId })
      .then((all) => {
        if (cancelled) return
        const sorted = [...all].sort((a, b) => a.fullName.localeCompare(b.fullName))
        setStudents(sorted)
        const initial: Record<string, AttendanceStatus> = {}
        sorted.forEach((s) => (initial[s.id] = 'PRESENT'))
        setStatuses(initial)
      })
      .catch((err) => {
        if (cancelled) return
        setStudents([])
        setError(err instanceof ApiError ? err.message : 'Could not load the students for this batch.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [batchId])

  function setStatus(studentId: string, status: AttendanceStatus) {
    setStatuses((prev) => ({ ...prev, [studentId]: status }))
  }

  async function handleSubmit() {
    setError(null)
    setSaving(true)
    setSaved(false)
    try {
      const entries = students.map((s) => ({ studentId: s.id, status: statuses[s.id] ?? 'PRESENT' }))
      const result = await api.post<StudentAttendanceRecord[]>('/api/v1/attendance', {
        batchId,
        sessionDate,
        entries,
      })
      setExisting(result)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save attendance.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <PageHeader title="Attendance" description="Mark today's roster for a batch in one go." />

      <div className="flex flex-wrap gap-4 mb-6 items-end">
        <div className="w-64">
          <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-48">
          <Input label="Session date" type="date" value={sessionDate} onChange={(e) => setSessionDate(e.target.value)} />
        </div>
        <Button variant="secondary" onClick={() => setQrOpen(true)} disabled={!batchId}>
          Show QR
        </Button>
      </div>

      <AttendanceQrModal open={qrOpen} onClose={() => setQrOpen(false)} batchId={batchId} sessionDate={sessionDate} />

      {error && <ErrorBanner message={error} />}
      {saved && (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
          Attendance saved for {existing?.length ?? 0} student{(existing?.length ?? 0) === 1 ? '' : 's'}.
        </div>
      )}

      {batches.length === 0 ? (
        <EmptyState title="No batches to mark attendance for" description="Create a batch first." />
      ) : loading ? (
        <LedgerSkeleton columns={2} />
      ) : students.length === 0 ? (
        <EmptyState title="No students in this batch" description="Add students to this batch to mark attendance." />
      ) : (
        <>
          <p className="text-sm text-slate mb-3">
            {students.length} student{students.length === 1 ? '' : 's'} in this batch
          </p>
          <Ledger columns={['Student', 'Status']}>
            {students.map((s) => (
              <Row key={s.id}>
                <Cell className="font-medium">{s.fullName}</Cell>
                <Cell>
                  <div className="flex gap-1.5">
                    {STATUSES.map((st) => (
                      <button
                        key={st}
                        onClick={() => setStatus(s.id, st)}
                        className={`rounded-full px-3 py-1 text-xs font-medium border transition-colors ${
                          statuses[s.id] === st
                            ? 'bg-ink text-paper border-ink'
                            : 'bg-white text-slate border-line hover:border-ink'
                        }`}
                      >
                        {st.charAt(0) + st.slice(1).toLowerCase()}
                      </button>
                    ))}
                  </div>
                </Cell>
              </Row>
            ))}
          </Ledger>

          <div className="mt-5 flex justify-end">
            <Button onClick={handleSubmit} disabled={saving}>
              {saving ? 'Saving…' : 'Save attendance'}
            </Button>
          </div>
        </>
      )}
    </Layout>
  )
}
