import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, Homework, Page } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import {
  Button,
  EmptyState,
  ErrorBanner,
  Input,
  Modal,
  Select,
  Skeleton,
  Textarea,
} from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'
import { formatDate, todayIso } from '@/lib/format'
import { HomeworkSubmissionsScreen } from '@/pages/teacher/HomeworkSubmissions'

export function HomeworkPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [items, setItems] = useState<Homework[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<Homework | null>(null)
  const [reviewing, setReviewing] = useState<Homework | null>(null)

  // Pagination state
  const [page, setPage] = useState(0)
  const [pageMeta, setPageMeta] = useState<Page<Homework> | null>(null)

  useEffect(() => {
    api
      .get<Page<Batch>>('/api/v1/batches', { size: 100 })
      .then((res) => {
        setBatches(res.content)

        if (res.content.length > 0) {
          setBatchId(res.content[0].id)
        } else {
          setLoading(false)
        }
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : 'Could not load batches.')
        setLoading(false)
      })
  }, [])

  // Load homework for the selected batch and page
  function load(forBatchId: string, p = page) {
    if (!forBatchId) return

    setLoading(true)
    setError(null)

    api
      .get<Page<Homework>>('/api/v1/homework', {
        batchId: forBatchId,
        page: p,
        size: PAGE_SIZE,
      })
      .then((res) => {
        // If the current page becomes empty after deletion,
        // navigate to the previous page.
        if (res.content.length === 0 && p > 0) {
          setPage(p - 1)
          return
        }

        setItems(res.content)
        setPageMeta(res)
      })
      .catch((err) => {
        setItems([])
        setPageMeta(null)
        setError(err instanceof ApiError ? err.message : 'Could not load homework.')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  // Reload when the batch or page changes
  useEffect(() => {
    if (batchId) {
      load(batchId, page)
    }
  }, [batchId, page])

  async function handleDelete(id: string) {
    if (!confirm('Delete this homework? This cannot be undone.')) return

    setError(null)
    try {
      await api.del(`/api/v1/homework/${id}`)
      load(batchId, page)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete this homework.')
    }
  }

  // Submissions review screen (full-page, replaces the list until the teacher goes back).
  if (reviewing) {
    return <HomeworkSubmissionsScreen homework={reviewing} onBack={() => setReviewing(null)} />
  }

  return (
    <Layout>
      <PageHeader
        title="Homework"
        description="Assignments for a batch. Every student in the batch sees the same homework."
        action={
          batches.length > 0 ? (
            <Button onClick={() => setCreateOpen(true)}>
              New homework
            </Button>
          ) : undefined
        }
      />

      {error && <ErrorBanner message={error} />}

      {batches.length === 0 ? (
        <EmptyState
          title="No batches yet"
          description="Create a batch first before assigning homework."
        />
      ) : (
        <>
          <div className="mb-6 max-w-xs">
            <Select
              label="Batch"
              value={batchId}
              onChange={(e) => {
                setBatchId(e.target.value)
                setPage(0)
              }}
            >
              {batches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </Select>
          </div>

          {loading ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-16 rounded-lg" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <EmptyState
              title="No homework for this batch"
              description="Assign something for students to work on."
              action={
                <Button onClick={() => setCreateOpen(true)}>
                  New homework
                </Button>
              }
            />
          ) : (
            <>
              <div className="space-y-2">
                {items.map((h) => (
                  <div
                    key={h.id}
                    className="border border-line rounded-lg bg-white p-4"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p className="font-medium text-ink">{h.title}</p>

                        {h.description && (
                          <p className="text-sm text-slate mt-1">
                            {h.description}
                          </p>
                        )}
                      </div>

                      <div className="flex items-center gap-3 whitespace-nowrap">
                        <span className="text-xs text-slate">
                          Due {formatDate(h.dueDate)}
                        </span>

                        <button
                          onClick={() => setReviewing(h)}
                          className="text-sm text-slate hover:text-ink underline underline-offset-2"
                        >
                          Submissions
                        </button>

                        <button
                          onClick={() => setEditing(h)}
                          className="text-sm text-slate hover:text-ink underline underline-offset-2"
                        >
                          Edit
                        </button>

                        <button
                          onClick={() => handleDelete(h.id)}
                          className="text-sm text-danger hover:opacity-80 underline underline-offset-2"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {/* Pagination controls */}
              {pageMeta && (
                <Pagination
                  page={pageMeta.number}
                  totalPages={pageMeta.totalPages}
                  totalElements={pageMeta.totalElements}
                  size={pageMeta.size}
                  onChange={setPage}
                />
              )}
            </>
          )}
        </>
      )}

      <CreateModal
        open={createOpen}
        batchId={batchId}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          load(batchId, page)
        }}
      />

      <EditModal
        homework={editing}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null)
          load(batchId, page)
        }}
      />
    </Layout>
  )
}

function CreateModal({
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
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState(todayIso())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)

    try {
      await api.post('/api/v1/homework', {
        batchId,
        title,
        description: description || undefined,
        dueDate,
      })

      setTitle('')
      setDescription('')
      setDueDate(todayIso())
      onCreated()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Could not create this homework.',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New homework">
      {error && <ErrorBanner message={error} />}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />

        <Textarea
          label="Instructions (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <Input
          label="Due date"
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          required
        />

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>

          <Button type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create homework'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function EditModal({
  homework,
  onClose,
  onSaved,
}: {
  homework: Homework | null
  onClose: () => void
  onSaved: () => void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (homework) {
      setTitle(homework.title)
      setDescription(homework.description ?? '')
      setDueDate(homework.dueDate)
      setError(null)
    }
  }, [homework])

  if (!homework) return null

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()

    if (!homework) return

    setError(null)
    setSaving(true)

    try {
      await api.patch(`/api/v1/homework/${homework.id}`, {
        title,
        description: description || undefined,
        dueDate,
      })

      onSaved()
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Could not save changes.',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={!!homework} onClose={onClose} title="Edit homework">
      {error && <ErrorBanner message={error} />}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />

        <Textarea
          label="Instructions (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <Input
          label="Due date"
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          required
        />

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>

          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
