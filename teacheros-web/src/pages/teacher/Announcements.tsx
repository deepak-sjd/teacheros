import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type {
  Announcement,
  AnnouncementAudience,
  AnnouncementReadStats,
  Batch,
  Page,
} from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select, Skeleton, Textarea } from '@/components/ui'
import { formatDateTime } from '@/lib/format'

function audienceLabel(a: Announcement, batches: Batch[]): string {
  if (a.audience === 'ALL') return 'Everyone'
  if (a.audience === 'BATCH') return batches.find((b) => b.id === a.batchId)?.name ?? 'Batch'
  return `Grade ${a.grade}`
}

export function AnnouncementsPage() {
  const [items, setItems] = useState<Announcement[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)

  function load() {
    setLoading(true)
    setError(null)
    Promise.all([
      api.get<Page<Announcement>>('/api/v1/announcements/manage', { size: 50 }),
      api.get<Page<Batch>>('/api/v1/batches', { size: 100 }),
    ])
      .then(([a, b]) => {
        setItems(a.content)
        setBatches(b.content)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load announcements.'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  return (
    <Layout>
      <PageHeader
        title="Announcements"
        description="Circulars for parents and students, with read receipts."
        action={<Button onClick={() => setCreateOpen(true)}>New announcement</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-lg" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          title="No announcements yet"
          description="Post a circular to reach everyone, one batch, or one class."
          action={<Button onClick={() => setCreateOpen(true)}>New announcement</Button>}
        />
      ) : (
        <div className="space-y-2">
          {items.map((a) => (
            <div key={a.id} className="border border-line rounded-lg bg-white p-4">
              <div className="flex items-start justify-between gap-4">
                <p className="font-medium text-ink">{a.title}</p>
                <span className="text-xs text-slate-light whitespace-nowrap">{formatDateTime(a.createdAt)}</span>
              </div>
              <p className="text-sm text-slate mt-1 whitespace-pre-wrap">{a.body}</p>
              <div className="flex items-center justify-between mt-3 text-xs text-slate-light">
                <span>
                  To: {audienceLabel(a, batches)} {'\u00b7'} by {a.createdByName}
                </span>
                <ReadStats announcementId={a.id} />
              </div>
            </div>
          ))}
        </div>
      )}

      <NewAnnouncementModal
        open={createOpen}
        batches={batches}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          load()
        }}
      />
    </Layout>
  )
}

function ReadStats({ announcementId }: { announcementId: string }) {
  const [stats, setStats] = useState<AnnouncementReadStats | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api
      .get<AnnouncementReadStats>(`/api/v1/announcements/${announcementId}/read-stats`)
      .then(setStats)
      .catch(() => setFailed(true))
  }, [announcementId])

  if (failed) return <span>Read count unavailable</span>
  if (!stats) return <span>Loading…</span>
  return (
    <span className="font-medium text-slate">
      Read by {stats.readCount} of {stats.recipientCount}
    </span>
  )
}

function NewAnnouncementModal({
  open,
  batches,
  onClose,
  onCreated,
}: {
  open: boolean
  batches: Batch[]
  onClose: () => void
  onCreated: () => void
}) {
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [audience, setAudience] = useState<AnnouncementAudience>('ALL')
  const [batchId, setBatchId] = useState('')
  const [grade, setGrade] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (audience === 'BATCH' && !batchId) {
      setError('Choose a batch.')
      return
    }
    if (audience === 'CLASS' && !grade.trim()) {
      setError('Enter a grade / class.')
      return
    }

    setSaving(true)
    try {
      await api.post('/api/v1/announcements', {
        title,
        body,
        audience,
        batchId: audience === 'BATCH' ? batchId : undefined,
        grade: audience === 'CLASS' ? grade.trim() : undefined,
      })
      setTitle('')
      setBody('')
      setAudience('ALL')
      setBatchId('')
      setGrade('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post this announcement.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New announcement">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} required />
        <Textarea
          label="Message"
          rows={5}
          value={body}
          maxLength={5000}
          onChange={(e) => setBody(e.target.value)}
          required
        />
        <Select label="Send to" value={audience} onChange={(e) => setAudience(e.target.value as AnnouncementAudience)}>
          <option value="ALL">Everyone</option>
          <option value="BATCH">One batch</option>
          <option value="CLASS">One grade / class</option>
        </Select>
        {audience === 'BATCH' && (
          <Select label="Batch" value={batchId} onChange={(e) => setBatchId(e.target.value)} required>
            <option value="">Choose a batch</option>
            {batches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </Select>
        )}
        {audience === 'CLASS' && (
          <Input
            label="Grade / class"
            placeholder="e.g. 10"
            value={grade}
            onChange={(e) => setGrade(e.target.value)}
            required
          />
        )}
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Posting…' : 'Post announcement'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
