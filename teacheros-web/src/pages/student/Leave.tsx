import { useEffect, useState, type FormEvent } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { Badge, Button, EmptyState, ErrorBanner, Input, Skeleton, Textarea } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { formatDate, formatDateTime, toLocalIsoDate, todayIso } from '@/lib/format'
import type { LeaveRequest, LeaveStatus, Student, SubmitLeaveRequest } from '@/lib/types'

// Matches LeaveRequestService.MAX_BACKDATE_DAYS - the server is the real check,
// this only keeps the date picker from offering days it will refuse.
const MAX_BACKDATE_DAYS = 7

const STATUS_TONE: Record<LeaveStatus, 'warn' | 'success' | 'danger'> = {
  PENDING: 'warn',
  APPROVED: 'success',
  REJECTED: 'danger',
}
const STATUS_LABEL: Record<LeaveStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Not approved',
}

function earliestDate(): string {
  const d = new Date()
  d.setDate(d.getDate() - MAX_BACKDATE_DAYS)
  return toLocalIsoDate(d)
}

function dateRange(l: LeaveRequest): string {
  return l.fromDate === l.toDate ? formatDate(l.fromDate) : `${formatDate(l.fromDate)} \u2013 ${formatDate(l.toDate)}`
}

function LeavePanel({ student }: { student: Student }) {
  const [requests, setRequests] = useState<LeaveRequest[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [fromDate, setFromDate] = useState(todayIso())
  const [toDate, setToDate] = useState(todayIso())
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  useEffect(() => {
    let cancelled = false
    setRequests(null)
    setLoadError(null)
    api
      .get<LeaveRequest[]>(`/api/v1/leave-requests/students/${student.id}`)
      .then((list) => {
        if (!cancelled) setRequests(list)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err instanceof ApiError ? err.message : 'Could not load leave requests.')
      })
    return () => {
      cancelled = true
    }
  }, [student.id])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSent(false)
    if (toDate < fromDate) {
      setFormError("The end date can't be before the start date.")
      return
    }
    if (reason.trim() === '') {
      setFormError('Tell the school why leave is needed.')
      return
    }
    setFormError(null)
    setSubmitting(true)
    const body: SubmitLeaveRequest = { fromDate, toDate, reason: reason.trim() }
    try {
      const created = await api.post<LeaveRequest>(`/api/v1/leave-requests/students/${student.id}`, body)
      setRequests((prev) => [created, ...(prev ?? [])])
      setReason('')
      setSent(true)
    } catch (err) {
      // Overlap, too-old and too-long come back from the server with clear messages.
      setFormError(err instanceof ApiError ? err.message : 'Could not send the request.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="max-w-2xl">
      <form onSubmit={handleSubmit} className="border border-line rounded-lg bg-white p-5 space-y-4 mb-8">
        <p className="text-sm font-medium text-ink">Request leave for {student.fullName}</p>
        {formError && <ErrorBanner message={formError} />}
        {sent && (
          <p className="text-sm text-success bg-success-soft rounded-lg px-4 py-3">
            Request sent. You&apos;ll get an alert when the school decides.
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="From"
            type="date"
            value={fromDate}
            min={earliestDate()}
            onChange={(e) => {
              setFromDate(e.target.value)
              if (toDate < e.target.value) setToDate(e.target.value)
            }}
            required
          />
          <Input label="To" type="date" value={toDate} min={fromDate} onChange={(e) => setToDate(e.target.value)} required />
        </div>
        <Textarea label="Reason" value={reason} maxLength={500} placeholder="e.g. Fever, doctor's advice to rest" onChange={(e) => setReason(e.target.value)} required />
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Sending…' : 'Send request'}
        </Button>
      </form>

      <h3 className="font-display text-xl text-ink mb-3">History</h3>
      {loadError && <ErrorBanner message={loadError} />}
      {requests === null && !loadError ? (
        <Skeleton className="h-24 rounded-lg" />
      ) : requests && requests.length === 0 ? (
        <EmptyState title="No leave requests yet" description="Requests you send will show up here with the school's decision." />
      ) : (
        <div className="space-y-2">
          {requests?.map((l) => (
            <div key={l.id} className="border border-line rounded-lg bg-white p-4">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-medium text-ink text-sm">{dateRange(l)}</p>
                  <p className="text-sm text-slate mt-1 whitespace-pre-wrap">{l.reason}</p>
                  {l.decisionNote && (
                    <p className="text-sm text-ink mt-2">
                      <span className="text-slate">School&apos;s note: </span>
                      {l.decisionNote}
                    </p>
                  )}
                  <p className="text-xs text-slate-light mt-2">
                    Requested {formatDateTime(l.createdAt)}
                    {l.decidedAt ? ` \u00b7 Decided ${formatDateTime(l.decidedAt)}` : ''}
                  </p>
                </div>
                <Badge tone={STATUS_TONE[l.status]}>{STATUS_LABEL[l.status]}</Badge>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// Parent / student view. The child switcher comes from StudentSectionShell; the
// backend 404s any studentId that isn't this account's own child / own record.
export function StudentLeave() {
  return (
    <StudentSectionShell title="Leave" description="Ask the school to excuse your child's absence.">
      {(student) => <LeavePanel key={student.id} student={student} />}
    </StudentSectionShell>
  )
}
