import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, Page } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select } from '@/components/ui'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

interface Slot {
  id: string
  batchId: string
  dayOfWeek: number
  startTime: string
  endTime: string
  subject: string | null
}

export function TimetablePage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [slots, setSlots] = useState<Slot[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)

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
            <p className="text-slate text-sm">Loading…</p>
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
                        <button
                          onClick={() => handleDelete(slot.id)}
                          className="text-danger hover:underline mt-1"
                        >
                          Remove
                        </button>
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
    </Layout>
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
