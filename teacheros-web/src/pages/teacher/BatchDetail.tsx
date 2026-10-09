import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { Batch, BatchSessionSummary, Page, Student, StudentAttendanceRecord } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import {
  Button,
  Cell,
  EmptyState,
  ErrorBanner,
  Input,
  Ledger,
  LedgerSkeleton,
  Modal,
  Row,
  Skeleton,
  StatBlock,
  StatusPill,
} from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'
import { formatDate } from '@/lib/format'

const DEFAULT_VISIBLE_SESSIONS = 10

export function BatchDetail() {
  const { id } = useParams<{ id: string }>()
  const batchId = id ?? ''
  const navigate = useNavigate()

  const [batch, setBatch] = useState<Batch | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [students, setStudents] = useState<Page<Student> | null>(null)
  const [rosterPage, setRosterPage] = useState(0)
  const [sessions, setSessions] = useState<BatchSessionSummary[] | null>(null)
  const [showAllSessions, setShowAllSessions] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [details, setDetails] = useState<Record<string, StudentAttendanceRecord[]>>({})
  const [names, setNames] = useState<Record<string, string>>({})
  const [allNamesLoaded, setAllNamesLoaded] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function mergeNames(list: Student[]) {
    setNames((prev) => {
      const next = { ...prev }
      list.forEach((s) => (next[s.id] = s.fullName))
      return next
    })
  }

  function loadBatch() {
    if (!batchId) return
    api
      .get<Batch>(`/api/v1/batches/${batchId}`)
      .then(setBatch)
      .catch(() => setNotFound(true))
  }

  function loadSessions() {
    if (!batchId) return
    api
      .get<BatchSessionSummary[]>(`/api/v1/attendance/batches/${batchId}/sessions`)
      .then(setSessions)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load attendance history.'))
  }

  useEffect(() => {
    setBatch(null)
    setNotFound(false)
    setSessions(null)
    setExpanded(null)
    setDetails({})
    setRosterPage(0)
    setAllNamesLoaded(false)
    setNames({})
    loadBatch()
    loadSessions()
  }, [batchId])

  useEffect(() => {
    if (!batchId) return
    setStudents(null)
    api
      .get<Page<Student>>('/api/v1/students', { batchId, page: rosterPage, size: PAGE_SIZE })
      .then((res) => {
        setStudents(res)
        mergeNames(res.content)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the roster.'))
  }, [batchId, rosterPage])

  // Session records only carry student ids. The roster page on screen may
  // not include everyone who was marked, so fetch the whole batch once,
  // the first time a session is opened.
  async function ensureNames() {
    if (allNamesLoaded) return
    const res = await api.get<Page<Student>>('/api/v1/students', { batchId, page: 0, size: 200 })
    mergeNames(res.content)
    setAllNamesLoaded(true)
  }

  async function toggleSession(sessionId: string) {
    if (expanded === sessionId) {
      setExpanded(null)
      return
    }
    setExpanded(sessionId)
    if (details[sessionId]) return
    try {
      const [records] = await Promise.all([
        api.get<StudentAttendanceRecord[]>(`/api/v1/attendance/sessions/${sessionId}`),
        ensureNames(),
      ])
      setDetails((prev) => ({ ...prev, [sessionId]: records }))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load that session.')
    }
  }

  async function handleDeactivate() {
    if (!batch) return
    if (!confirm(`Deactivate "${batch.name}"? Its history is kept, but it will disappear from your batch lists.`)) {
      return
    }
    setError(null)
    try {
      await api.del(`/api/v1/batches/${batchId}`)
      navigate('/app/batches')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not deactivate this batch.')
    }
  }

  if (notFound) {
    return (
      <Layout>
        <EmptyState
          title="Batch not found"
          description="It may have been deactivated."
          action={
            <Link to="/app/batches">
              <Button variant="secondary">Back to batches</Button>
            </Link>
          }
        />
      </Layout>
    )
  }

  const totalMarked = sessions?.reduce((sum, s) => sum + s.totalCount, 0) ?? 0
  const totalAttended = sessions?.reduce((sum, s) => sum + s.presentCount + s.lateCount, 0) ?? 0
  const averageAttendance = totalMarked > 0 ? `${Math.round((totalAttended * 100) / totalMarked)}%` : '\u2014'
  const visibleSessions = sessions ? (showAllSessions ? sessions : sessions.slice(0, DEFAULT_VISIBLE_SESSIONS)) : []

  return (
    <Layout>
      <Link to="/app/batches" className="text-sm text-slate hover:text-ink underline underline-offset-2 mb-6 inline-block">
        ← All batches
      </Link>

      <PageHeader
        title={batch?.name ?? 'Batch'}
        description={
          batch ? [batch.subject, batch.scheduleSummary].filter(Boolean).join(' \u00b7 ') || 'No subject or schedule set' : undefined
        }
        action={
          batch && (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setEditOpen(true)}>
                Edit
              </Button>
              <Button variant="danger" onClick={handleDeactivate}>
                Deactivate
              </Button>
            </div>
          )
        }
      />

      {error && <ErrorBanner message={error} />}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <StatBlock label="Students" value={students ? String(students.totalElements) : '\u2014'} />
        <StatBlock label="Sessions held" value={sessions ? String(sessions.length) : '\u2014'} />
        <StatBlock label="Average attendance" value={sessions ? averageAttendance : '\u2014'} />
      </div>

      <h3 className="font-display text-xl text-ink mb-3">Roster</h3>
      {students === null ? (
        <LedgerSkeleton columns={3} />
      ) : students.totalElements === 0 ? (
        <EmptyState title="No students in this batch" description="Assign students to this batch from the Students page." />
      ) : (
        <>
          <Ledger columns={['Name', 'Grade', '']}>
            {students.content.map((s) => (
              <Row key={s.id}>
                <Cell className="font-medium">{s.fullName}</Cell>
                <Cell>{s.grade || '\u2014'}</Cell>
                <Cell>
                  <Link to={`/app/students/${s.id}`} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                    View
                  </Link>
                </Cell>
              </Row>
            ))}
          </Ledger>
          <Pagination
            page={students.number}
            totalPages={students.totalPages}
            totalElements={students.totalElements}
            size={students.size}
            onChange={setRosterPage}
          />
        </>
      )}

      <h3 className="font-display text-xl text-ink mt-10 mb-3">Attendance history</h3>
      {sessions === null ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-14 rounded-lg" />
          ))}
        </div>
      ) : sessions.length === 0 ? (
        <EmptyState title="No sessions yet" description="Mark attendance for this batch to start building history." />
      ) : (
        <>
          <div className="space-y-2">
            {visibleSessions.map((s) => {
              const records = details[s.classSessionId]
              return (
                <div key={s.classSessionId} className="border border-line rounded-lg bg-white">
                  <button
                    onClick={() => toggleSession(s.classSessionId)}
                    className="w-full text-left p-4 flex items-center justify-between gap-4"
                  >
                    <span className="font-medium text-ink text-sm">{formatDate(s.sessionDate)}</span>
                    <span className="text-xs text-slate">
                      {s.presentCount} present {'\u00b7'} {s.absentCount} absent {'\u00b7'} {s.lateCount} late
                    </span>
                  </button>
                  {expanded === s.classSessionId && (
                    <div className="border-t border-line p-4">
                      {!records ? (
                        <Skeleton className="h-24 rounded-md" />
                      ) : records.length === 0 ? (
                        <p className="text-sm text-slate">No records for this session.</p>
                      ) : (
                        <Ledger columns={['Student', 'Status']}>
                          {records.map((r) => (
                            <Row key={r.studentId}>
                              <Cell>{names[r.studentId] ?? r.studentId.slice(0, 8)}</Cell>
                              <Cell>
                                <StatusPill status={r.status} />
                              </Cell>
                            </Row>
                          ))}
                        </Ledger>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
          {sessions.length > DEFAULT_VISIBLE_SESSIONS && (
            <div className="mt-3">
              <button
                onClick={() => setShowAllSessions((v) => !v)}
                className="text-sm text-slate hover:text-ink underline underline-offset-2"
              >
                {showAllSessions ? 'Show fewer' : `Show all ${sessions.length} sessions`}
              </button>
            </div>
          )}
        </>
      )}

      <EditBatchModal
        open={editOpen}
        batch={batch}
        onClose={() => setEditOpen(false)}
        onSaved={(updated) => {
          setEditOpen(false)
          setBatch(updated)
        }}
      />
    </Layout>
  )
}

function EditBatchModal({
  open,
  batch,
  onClose,
  onSaved,
}: {
  open: boolean
  batch: Batch | null
  onClose: () => void
  onSaved: (batch: Batch) => void
}) {
  const [name, setName] = useState('')
  const [subject, setSubject] = useState('')
  const [scheduleSummary, setScheduleSummary] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open || !batch) return
    setName(batch.name)
    setSubject(batch.subject ?? '')
    setScheduleSummary(batch.scheduleSummary ?? '')
    setError(null)
  }, [open, batch])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!batch) return
    setError(null)
    setSaving(true)
    try {
      const updated = await api.patch<Batch>(`/api/v1/batches/${batch.id}`, {
        name,
        subject: subject || undefined,
        scheduleSummary: scheduleSummary || undefined,
      })
      onSaved(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save changes.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Edit batch">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Batch name" value={name} onChange={(e) => setName(e.target.value)} required />
        <Input label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} />
        <Input label="Schedule" value={scheduleSummary} onChange={(e) => setScheduleSummary(e.target.value)} />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving\u2026' : 'Save changes'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
