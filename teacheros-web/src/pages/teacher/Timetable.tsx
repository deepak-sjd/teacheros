import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, MeetingPlatform, Page } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select, Skeleton } from '@/components/ui'
import { MEETING_PLATFORM_LABELS } from '@/lib/format'
import { SlotJoinsModal } from '@/components/SlotJoinsModal'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const MEETING_PLATFORMS: MeetingPlatform[] = ['ZOOM', 'GOOGLE_MEET', 'MICROSOFT_TEAMS', 'OTHER']

interface Slot {
  id: string
  batchId: string
  dayOfWeek: number
  startTime: string
  endTime: string
  subject: string | null
  meetingLink: string | null
  meetingPlatform: MeetingPlatform | null
}

export function TimetablePage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [slots, setSlots] = useState<Slot[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [linkSlot, setLinkSlot] = useState<Slot | null>(null)
  const [joinsSlot, setJoinsSlot] = useState<Slot | null>(null)

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => {
      setBatches(res.content)
      if (res.content.length > 0) setBatchId(res.content[0].id)
      else setLoading(false)
    })
  }, [])

  function load(forBatchId: string) {
    if (!forBatchId) return
    setLoading(true)
    api
      .get<Slot[]>(`/api/v1/timetable/batches/${forBatchId}`)
      .then(setSlots)
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (batchId) load(batchId)
  }, [batchId])

  async function handleDelete(id: string) {
    if (!confirm('Remove this slot?')) return
    await api.del(`/api/v1/timetable/${id}`)
    load(batchId)
  }

  const slotsByDay = DAYS.map((_, i) => slots.filter((s) => s.dayOfWeek === i + 1))

  return (
    <Layout>
      <PageHeader
        title="Timetable"
        description="Weekly schedule for a batch."
        action={batches.length > 0 ? <Button onClick={() => setCreateOpen(true)}>New slot</Button> : undefined}
      />

      {batches.length === 0 ? (
        <EmptyState title="No batches yet" description="Create a batch first before setting a schedule." />
      ) : (
        <>
          <div className="mb-6 max-w-xs">
            <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
              {batches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </Select>
          </div>

          {loading ? (
            <div className="overflow-x-auto">
              <div className="grid grid-cols-7 gap-3 min-w-[640px]">
                {DAYS.map((day) => (
                  <div key={day}>
                    <Skeleton className="h-4 w-8 mb-2" />
                    <Skeleton className="h-14 rounded-md" />
                  </div>
                ))}
              </div>
            </div>
          ) : slots.length === 0 ? (
            <EmptyState
              title="No schedule set"
              description="Add the first slot for this batch."
              action={<Button onClick={() => setCreateOpen(true)}>New slot</Button>}
            />
          ) : (
            <div className="overflow-x-auto">
            <div className="grid grid-cols-7 gap-3 min-w-[640px]">
              {DAYS.map((day, i) => (
                <div key={day}>
                  <div className="text-sm font-medium text-slate mb-2">{day}</div>
                  <div className="space-y-2">
                    {slotsByDay[i].map((slot) => (
                      <div key={slot.id} className="rounded-md border border-line bg-white p-2 text-xs">
                        <div className="font-medium text-ink">
                          {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                        </div>
                        {slot.subject && <div className="text-slate">{slot.subject}</div>}
                        {slot.meetingLink && (
                          <div className="mt-1 inline-flex items-center gap-1 rounded-full bg-success-soft text-success px-2 py-0.5">
                            {MEETING_PLATFORM_LABELS[slot.meetingPlatform ?? 'OTHER']}
                          </div>
                        )}
                        <div className="flex items-center gap-2 mt-1.5">
                          <button onClick={() => setLinkSlot(slot)} className="text-slate hover:text-ink hover:underline">
                            {slot.meetingLink ? 'Edit link' : 'Add link'}
                          </button>
                          <button onClick={() => setJoinsSlot(slot)} className="text-slate hover:text-ink hover:underline">
                            Who joined
                          </button>
                          <button onClick={() => handleDelete(slot.id)} className="text-danger hover:underline">
                            Remove
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
            </div>
          )}
        </>
      )}

      <NewSlotModal
        open={createOpen}
        batchId={batchId}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          load(batchId)
        }}
      />

      <EditMeetingLinkModal
        slot={linkSlot}
        onClose={() => setLinkSlot(null)}
        onSaved={() => {
          setLinkSlot(null)
          load(batchId)
        }}
      />

      <SlotJoinsModal slot={joinsSlot} onClose={() => setJoinsSlot(null)} />
    </Layout>
  )
}

function EditMeetingLinkModal({
  slot,
  onClose,
  onSaved,
}: {
  slot: Slot | null
  onClose: () => void
  onSaved: () => void
}) {
  const [platform, setPlatform] = useState<MeetingPlatform>('ZOOM')
  const [link, setLink] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (slot) {
      setPlatform(slot.meetingPlatform ?? 'ZOOM')
      setLink(slot.meetingLink ?? '')
      setError(null)
    }
  }, [slot])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!slot) return
    setError(null)
    setSaving(true)
    try {
      await api.patch(`/api/v1/timetable/${slot.id}/meeting-link`, {
        meetingLink: link || undefined,
        meetingPlatform: link ? platform : undefined,
      })
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the meeting link.')
    } finally {
      setSaving(false)
    }
  }

  async function handleRemove() {
    if (!slot) return
    setSaving(true)
    try {
      await api.patch(`/api/v1/timetable/${slot.id}/meeting-link`, {})
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove the meeting link.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={slot !== null} onClose={onClose} title="Video call link">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Select label="Platform" value={platform} onChange={(e) => setPlatform(e.target.value as MeetingPlatform)}>
          {MEETING_PLATFORMS.map((p) => (
            <option key={p} value={p}>
              {MEETING_PLATFORM_LABELS[p]}
            </option>
          ))}
        </Select>
        <Input
          label="Meeting link"
          type="url"
          placeholder="https://zoom.us/j/…"
          value={link}
          onChange={(e) => setLink(e.target.value)}
        />
        <p className="text-xs text-slate-light">
          Students and parents will see a "Join" button on this slot whenever class is on.
        </p>
        <div className="flex gap-2 justify-between pt-2">
          {slot?.meetingLink ? (
            <Button type="button" variant="danger" onClick={handleRemove} disabled={saving}>
              Remove link
            </Button>
          ) : (
            <span />
          )}
          <div className="flex gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : 'Save link'}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  )
}

function NewSlotModal({
  open,
  batchId,
  onClose,
  onCreated,
}: {
  open: boolean
  batchId: string
  onClose: () => void
  onCreated: () => void
}) {
  const [dayOfWeek, setDayOfWeek] = useState('1')
  const [startTime, setStartTime] = useState('16:00')
  const [endTime, setEndTime] = useState('17:00')
  const [subject, setSubject] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      await api.post('/api/v1/timetable', {
        batchId,
        dayOfWeek: Number(dayOfWeek),
        startTime: `${startTime}:00`,
        endTime: `${endTime}:00`,
        subject: subject || undefined,
      })
      setSubject('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create the slot.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New timetable slot">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Select label="Day" value={dayOfWeek} onChange={(e) => setDayOfWeek(e.target.value)}>
          {DAYS.map((d, i) => (
            <option key={d} value={i + 1}>
              {d}
            </option>
          ))}
        </Select>
        <div className="grid grid-cols-2 gap-3">
          <Input label="Start time" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
          <Input label="End time" type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} required />
        </div>
        <Input label="Subject (optional)" value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Defaults to batch subject" />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create slot'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
