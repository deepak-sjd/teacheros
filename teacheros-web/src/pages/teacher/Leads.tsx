import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { api, ApiError } from '@/lib/api'
import type {
  Batch,
  ConvertLeadResult,
  Lead,
  LeadAssignee,
  LeadCounts,
  LeadDetail,
  LeadSource,
  LeadStatus,
  Page,
} from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Badge, Button, EmptyState, ErrorBanner, Input, Modal, Select, Skeleton, Textarea } from '@/components/ui'
import { formatDate, formatDateTime, todayIso } from '@/lib/format'

const STATUSES: LeadStatus[] = ['NEW', 'CONTACTED', 'DEMO', 'ENROLLED', 'LOST']
const STATUS_LABEL: Record<LeadStatus, string> = {
  NEW: 'New',
  CONTACTED: 'Contacted',
  DEMO: 'Demo',
  ENROLLED: 'Enrolled',
  LOST: 'Lost',
}
const STATUS_TONE: Record<LeadStatus, 'brand' | 'warn' | 'neutral' | 'success' | 'danger'> = {
  NEW: 'brand',
  CONTACTED: 'warn',
  DEMO: 'neutral',
  ENROLLED: 'success',
  LOST: 'danger',
}
const SOURCE_LABEL: Record<LeadSource, string> = {
  WEBSITE: 'Website',
  COURSE_PAGE: 'Course page',
  WALK_IN: 'Walk-in',
  REFERRAL: 'Referral',
  OTHER: 'Other',
}

type Mode = 'status' | 'assign' | 'followup' | 'convert' | 'history'
interface Target {
  lead: Lead
  mode: Mode
}

const MODAL_TITLE: Record<Mode, string> = {
  status: 'Update status',
  assign: 'Assign lead',
  followup: 'Schedule follow-up',
  convert: 'Convert to student',
  history: 'Lead history',
}

export function LeadsPage() {
  const [filter, setFilter] = useState<LeadStatus | 'ALL'>('ALL')
  const [assigneeFilter, setAssigneeFilter] = useState('')
  const [leads, setLeads] = useState<Lead[] | null>(null)
  const [counts, setCounts] = useState<Record<LeadStatus, number> | null>(null)
  const [assignees, setAssignees] = useState<LeadAssignee[]>([])
  const [error, setError] = useState<string | null>(null)
  const [target, setTarget] = useState<Target | null>(null)

  function load() {
    setError(null)
    Promise.all([
      api.get<Page<Lead>>('/api/v1/leads', {
        status: filter === 'ALL' ? undefined : filter,
        assignedTo: assigneeFilter || undefined,
        size: 100,
      }),
      api.get<LeadCounts>('/api/v1/leads/counts'),
    ])
      .then(([page, c]) => {
        setLeads(page.content)
        setCounts(c.counts)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load leads.'))
  }

  useEffect(() => {
    setLeads(null)
    load()
  }, [filter, assigneeFilter]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    api.get<LeadAssignee[]>('/api/v1/leads/assignees').then(setAssignees).catch(() => {})
  }, [])

  const total = counts ? STATUSES.reduce((sum, s) => sum + counts[s], 0) : 0
  const today = todayIso()

  return (
    <Layout>
      <PageHeader title="Leads" description="Enquiries from your website and walk-ins, from first contact to enrolment." />

      <div className="flex flex-wrap gap-2 mb-5">
        {(['ALL', ...STATUSES] as const).map((s) => {
          const active = filter === s
          const count = s === 'ALL' ? total : (counts?.[s] ?? 0)
          return (
            <button
              key={s}
              onClick={() => setFilter(s)}
              className={`inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 text-sm font-medium border transition-all duration-150 active:scale-95 ${
                active
                  ? 'bg-ink text-paper border-ink shadow-sm'
                  : 'bg-white text-slate border-line hover:border-ink/40 hover:text-ink'
              }`}
            >
              {s === 'ALL' ? 'All' : STATUS_LABEL[s]}
              <span className={`text-xs tabular-nums ${active ? 'text-paper/70' : 'text-slate-light'}`}>{count}</span>
            </button>
          )
        })}
      </div>

      <div className="mb-6 max-w-xs">
        <Select label="Assigned to" value={assigneeFilter} onChange={(e) => setAssigneeFilter(e.target.value)}>
          <option value="">Everyone</option>
          {assignees.map((a) => (
            <option key={a.id} value={a.id}>
              {a.displayName}
            </option>
          ))}
        </Select>
      </div>

      {error && <ErrorBanner message={error} />}

      {leads === null ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      ) : leads.length === 0 ? (
        <EmptyState
          title="No leads here"
          description="New enquiries from your website will show up in this pipeline automatically."
        />
      ) : (
        <div className="space-y-4">
          {leads.map((lead) => {
            const open = lead.status !== 'ENROLLED' && lead.status !== 'LOST'
            const overdue = open && lead.nextFollowUp !== null && lead.nextFollowUp < today
            return (
              <div key={lead.id} className="border border-line rounded-xl bg-white p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium text-ink text-base">{lead.name}</p>
                      <Badge tone={STATUS_TONE[lead.status]}>{STATUS_LABEL[lead.status]}</Badge>
                      <Badge>{SOURCE_LABEL[lead.source]}</Badge>
                    </div>
                    <p className="text-sm text-slate mt-1">
                      <a href={`tel:${lead.phone}`} className="hover:text-ink underline underline-offset-2">
                        {lead.phone}
                      </a>
                      {lead.email && <> {'\u00b7'} {lead.email}</>}
                    </p>
                  </div>
                  <span className="text-xs text-slate-light whitespace-nowrap">{formatDateTime(lead.createdAt)}</span>
                </div>

                {lead.message && <p className="text-sm text-slate mt-3 line-clamp-2">{lead.message}</p>}

                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-3 text-xs text-slate-light">
                  <span>{lead.assignedToName ? `Assigned to ${lead.assignedToName}` : 'Unassigned'}</span>
                  {lead.nextFollowUp && (
                    <span className={overdue ? 'text-danger font-medium' : ''}>
                      {overdue ? 'Follow-up overdue: ' : 'Follow-up: '}
                      {formatDate(lead.nextFollowUp)}
                    </span>
                  )}
                </div>

                <div className="flex flex-wrap items-center gap-2 mt-4">
                  {open && (
                    <Button variant="secondary" onClick={() => setTarget({ lead, mode: 'status' })}>
                      Update status
                    </Button>
                  )}
                  <Button variant="secondary" onClick={() => setTarget({ lead, mode: 'assign' })}>
                    Assign
                  </Button>
                  {open && (
                    <Button variant="secondary" onClick={() => setTarget({ lead, mode: 'followup' })}>
                      Follow-up
                    </Button>
                  )}
                  {lead.status !== 'ENROLLED' && (
                    <Button onClick={() => setTarget({ lead, mode: 'convert' })}>Convert to student</Button>
                  )}
                  <Button variant="ghost" onClick={() => setTarget({ lead, mode: 'history' })}>
                    History
                  </Button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      <Modal open={target !== null} onClose={() => setTarget(null)} title={target ? MODAL_TITLE[target.mode] : ''}>
        {target?.mode === 'status' && <StatusForm lead={target.lead} onCancel={() => setTarget(null)} onDone={done} />}
        {target?.mode === 'assign' && (
          <AssignForm lead={target.lead} assignees={assignees} onCancel={() => setTarget(null)} onDone={done} />
        )}
        {target?.mode === 'followup' && <FollowUpForm lead={target.lead} onCancel={() => setTarget(null)} onDone={done} />}
        {target?.mode === 'convert' && <ConvertForm lead={target.lead} onCancel={() => setTarget(null)} onDone={done} />}
        {target?.mode === 'history' && <HistoryView lead={target.lead} onClose={() => setTarget(null)} />}
      </Modal>
    </Layout>
  )

  function done() {
    setTarget(null)
    load()
  }
}

function FormShell({
  error,
  saving,
  submitLabel,
  onCancel,
  onSubmit,
  children,
}: {
  error: string | null
  saving: boolean
  submitLabel: string
  onCancel: () => void
  onSubmit: (e: FormEvent) => void
  children: ReactNode
}) {
  return (
    <>
      {error && <ErrorBanner message={error} />}
      <form onSubmit={onSubmit} className="space-y-4">
        {children}
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving\u2026' : submitLabel}
          </Button>
        </div>
      </form>
    </>
  )
}

function useSubmit(action: () => Promise<unknown>, onDone: () => void, fallback: string) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await action()
      onDone()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : fallback)
      setSaving(false)
    }
  }
  return { saving, error, submit }
}

function StatusForm({ lead, onCancel, onDone }: { lead: Lead; onCancel: () => void; onDone: () => void }) {
  const options = STATUSES.filter((s) => s !== 'ENROLLED' && s !== lead.status)
  const [status, setStatus] = useState<LeadStatus>(options[0])
  const [note, setNote] = useState('')
  const { saving, error, submit } = useSubmit(
    () => api.patch(`/api/v1/leads/${lead.id}/status`, { status, note: note || undefined }),
    onDone,
    'Could not update the status.',
  )
  return (
    <FormShell error={error} saving={saving} submitLabel="Update" onCancel={onCancel} onSubmit={submit}>
      <Select label="New status" value={status} onChange={(e) => setStatus(e.target.value as LeadStatus)}>
        {options.map((s) => (
          <option key={s} value={s}>
            {STATUS_LABEL[s]}
          </option>
        ))}
      </Select>
      <Textarea label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
    </FormShell>
  )
}

function AssignForm({
  lead,
  assignees,
  onCancel,
  onDone,
}: {
  lead: Lead
  assignees: LeadAssignee[]
  onCancel: () => void
  onDone: () => void
}) {
  const [userId, setUserId] = useState(lead.assignedTo ?? '')
  const { saving, error, submit } = useSubmit(
    () => api.patch(`/api/v1/leads/${lead.id}/assign`, { userId: userId || null }),
    onDone,
    'Could not assign this lead.',
  )
  return (
    <FormShell error={error} saving={saving} submitLabel="Save" onCancel={onCancel} onSubmit={submit}>
      <Select label="Assign to" value={userId} onChange={(e) => setUserId(e.target.value)}>
        <option value="">Unassigned</option>
        {assignees.map((a) => (
          <option key={a.id} value={a.id}>
            {a.displayName}
          </option>
        ))}
      </Select>
    </FormShell>
  )
}

function FollowUpForm({ lead, onCancel, onDone }: { lead: Lead; onCancel: () => void; onDone: () => void }) {
  const [date, setDate] = useState(lead.nextFollowUp ?? todayIso())
  const [note, setNote] = useState('')
  const { saving, error, submit } = useSubmit(
    () => api.post(`/api/v1/leads/${lead.id}/follow-up`, { nextFollowUp: date, note: note || undefined }),
    onDone,
    'Could not schedule the follow-up.',
  )
  return (
    <FormShell error={error} saving={saving} submitLabel="Schedule" onCancel={onCancel} onSubmit={submit}>
      <Input label="Follow up on" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
      <Textarea label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
    </FormShell>
  )
}

function ConvertForm({ lead, onCancel, onDone }: { lead: Lead; onCancel: () => void; onDone: () => void }) {
  const [studentName, setStudentName] = useState(lead.name)
  const [grade, setGrade] = useState('')
  const [batchId, setBatchId] = useState('')
  const [batches, setBatches] = useState<Batch[]>([])

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => setBatches(res.content)).catch(() => {})
  }, [])

  const { saving, error, submit } = useSubmit(
    () =>
      api.post<ConvertLeadResult>(`/api/v1/leads/${lead.id}/convert`, {
        studentName: studentName || undefined,
        grade: grade || undefined,
        batchId: batchId || undefined,
      }),
    onDone,
    'Could not convert this lead.',
  )
  return (
    <FormShell error={error} saving={saving} submitLabel="Create student" onCancel={onCancel} onSubmit={submit}>
      <p className="text-sm text-slate">
        Creates a student with <span className="text-ink font-medium">{lead.phone}</span> as the guardian phone, so the
        parent can log in with it straight away.
      </p>
      <Input label="Student name" value={studentName} onChange={(e) => setStudentName(e.target.value)} required />
      <Input label="Grade / class (optional)" value={grade} onChange={(e) => setGrade(e.target.value)} placeholder="e.g. 10" />
      <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
        <option value="">No batch yet</option>
        {batches.map((b) => (
          <option key={b.id} value={b.id}>
            {b.name}
          </option>
        ))}
      </Select>
    </FormShell>
  )
}

function HistoryView({ lead, onClose }: { lead: Lead; onClose: () => void }) {
  const [detail, setDetail] = useState<LeadDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<LeadDetail>(`/api/v1/leads/${lead.id}`)
      .then(setDetail)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the history.'))
  }, [lead.id])

  return (
    <>
      {error && <ErrorBanner message={error} />}
      {detail === null && !error ? (
        <div className="space-y-2">
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
        </div>
      ) : (
        <ol className="space-y-3 border-l border-line pl-4">
          {detail?.history.map((h) => (
            <li key={h.id} className="relative">
              <span className="absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full bg-chalk ring-4 ring-white" />
              <p className="text-sm text-ink">
                {h.fromStatus === h.toStatus ? (
                  h.note
                ) : (
                  <>
                    {h.fromStatus ? `${STATUS_LABEL[h.fromStatus]} \u2192 ` : ''}
                    <span className="font-medium">{STATUS_LABEL[h.toStatus]}</span>
                    {h.note ? ` \u2014 ${h.note}` : ''}
                  </>
                )}
              </p>
              <p className="text-xs text-slate-light mt-0.5">
                {formatDateTime(h.changedAt)}
                {h.changedByName ? ` \u00b7 ${h.changedByName}` : ''}
              </p>
            </li>
          ))}
        </ol>
      )}
      <div className="flex justify-end pt-4">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </>
  )
}
