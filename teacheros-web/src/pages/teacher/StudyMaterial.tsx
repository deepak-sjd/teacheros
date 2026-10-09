import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, DownloadUrlResponse, GenerateNotesResponse, Page, StudyMaterial } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select, Skeleton, Textarea } from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'
import { formatDate } from '@/lib/format'

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function StudyMaterialPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [items, setItems] = useState<StudyMaterial[]>([])
  const [loading, setLoading] = useState(true)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [generateOpen, setGenerateOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [pageMeta, setPageMeta] = useState<Page<StudyMaterial> | null>(null)

  useEffect(() => {
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => {
      setBatches(res.content)
      if (res.content.length > 0) setBatchId(res.content[0].id)
      else setLoading(false)
    })
  }, [])

  function load(forBatchId: string, p = page) {
    if (!forBatchId) return
    setLoading(true)
    api
      .get<Page<StudyMaterial>>('/api/v1/study-material', { batchId: forBatchId, page: p, size: PAGE_SIZE })
      .then((res) => {
        // Last item on the page was deleted: step back one page (effect will refetch)
        if (res.content.length === 0 && p > 0) {
          setPage(p - 1)
          return
        }
        setItems(res.content)
        setPageMeta(res)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (batchId) load(batchId, page)
  }, [batchId, page])

  async function handleDownload(id: string) {
    const { url } = await api.get<DownloadUrlResponse>(`/api/v1/study-material/${id}/download-url`)
    window.open(url, '_blank')
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this file? This cannot be undone.')) return
    await api.del(`/api/v1/study-material/${id}`)
    load(batchId)
  }

  return (
    <Layout>
      <PageHeader
        title="Study material"
        description="Notes, PDFs, and reference documents for a batch. Every student in the batch can view and download."
        action={
          batches.length > 0 ? (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setGenerateOpen(true)}>
                Generate notes with AI
              </Button>
              <Button onClick={() => setUploadOpen(true)}>Upload file</Button>
            </div>
          ) : undefined
        }
      />

      {batches.length === 0 ? (
        <EmptyState title="No batches yet" description="Create a batch first before uploading material." />
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
              title="No files for this batch"
              description="Upload notes, PDFs, or reference material for students to access."
              action={<Button onClick={() => setUploadOpen(true)}>Upload file</Button>}
            />
          ) : (
            <>
              <div className="space-y-2">
                {items.map((m) => (
                  <div key={m.id} className="border border-line rounded-lg bg-white p-4">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p className="font-medium text-ink">{m.title}</p>
                        {m.description && <p className="text-sm text-slate mt-1">{m.description}</p>}
                        <p className="text-xs text-slate mt-1">
                          {m.originalFilename} · {formatFileSize(m.fileSizeBytes)} · Uploaded {formatDate(m.createdAt)}
                        </p>
                      </div>
                      <div className="flex items-center gap-3 whitespace-nowrap">
                        <button
                          onClick={() => handleDownload(m.id)}
                          className="text-sm text-slate hover:text-ink underline underline-offset-2"
                        >
                          Download
                        </button>
                        <button
                          onClick={() => handleDelete(m.id)}
                          className="text-sm text-danger hover:opacity-80 underline underline-offset-2"
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

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

      <UploadModal
        open={uploadOpen}
        batchId={batchId}
        onClose={() => setUploadOpen(false)}
        onUploaded={() => {
          setUploadOpen(false)
          load(batchId)
        }}
      />

      <GenerateNotesModal
        open={generateOpen}
        batchId={batchId}
        onClose={() => setGenerateOpen(false)}
        onSaved={() => {
          setGenerateOpen(false)
          load(batchId)
        }}
      />
    </Layout>
  )
}

function UploadModal({
  open,
  batchId,
  onClose,
  onUploaded,
}: {
  open: boolean
  batchId: string
  onClose: () => void
  onUploaded: () => void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (!file) {
      setError('Choose a file to upload.')
      return
    }

    const formData = new FormData()
    formData.append('batchId', batchId)
    formData.append('title', title)
    if (description) formData.append('description', description)
    formData.append('file', file)

    setUploading(true)
    try {
      await api.uploadFile('/api/v1/study-material', formData)
      setTitle('')
      setDescription('')
      setFile(null)
      onUploaded()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not upload this file.')
    } finally {
      setUploading(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Upload study material">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <Textarea
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <label className="block">
          <span className="block text-sm font-medium text-ink mb-1.5">File</span>
          <input
            type="file"
            accept=".pdf,.doc,.docx,.png,.jpg,.jpeg,.webp"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="block w-full text-sm text-slate file:mr-3 file:rounded-md file:border-0 file:bg-paper-dim file:px-3 file:py-2 file:text-sm file:font-medium file:text-ink hover:file:bg-line"
            required
          />
          <span className="block text-xs text-slate mt-1">PDF, Word, or image. Max 20MB.</span>
        </label>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={uploading}>
            {uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * Draft, review/edit, then save - the same two-step shape as
 * GenerateQuestionsModal's draft-then-review flow in teacher/Tests.tsx,
 * except here "save" means uploading the edited text as a plain-text file
 * through the existing study-material upload endpoint (see
 * AllowedContentTypes - text/plain was added specifically for this),
 * rather than a separate "AI notes" storage path.
 */
function GenerateNotesModal({
  open,
  batchId,
  onClose,
  onSaved,
}: {
  open: boolean
  batchId: string
  onClose: () => void
  onSaved: () => void
}) {
  const [step, setStep] = useState<'draft' | 'review'>('draft')
  const [topic, setTopic] = useState('')
  const [subject, setSubject] = useState('')
  const [grade, setGrade] = useState('')
  const [notes, setNotes] = useState('')
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [generating, setGenerating] = useState(false)
  const [saving, setSaving] = useState(false)

  function reset() {
    setStep('draft')
    setTopic('')
    setSubject('')
    setGrade('')
    setNotes('')
    setTitle('')
    setError(null)
  }

  function handleClose() {
    reset()
    onClose()
  }

  async function handleGenerate(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!topic.trim()) {
      setError('Enter a topic to generate notes about.')
      return
    }

    setGenerating(true)
    try {
      const res = await api.post<GenerateNotesResponse>('/api/v1/content-generator/notes', {
        topic,
        subject: subject || undefined,
        grade: grade || undefined,
      })
      setNotes(res.notes)
      setTitle(topic)
      setStep('review')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not generate notes. Please try again.')
    } finally {
      setGenerating(false)
    }
  }

  async function handleSave() {
    setError(null)
    if (!title.trim()) {
      setError('Enter a title for this study material.')
      return
    }
    if (!notes.trim()) {
      setError('Notes cannot be empty.')
      return
    }

    const file = new File([notes], `${title.trim().slice(0, 80)}.txt`, { type: 'text/plain' })
    const formData = new FormData()
    formData.append('batchId', batchId)
    formData.append('title', title)
    formData.append('description', 'Generated with AI, reviewed and edited by the teacher.')
    formData.append('file', file)

    setSaving(true)
    try {
      await api.uploadFile('/api/v1/study-material', formData)
      reset()
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save these notes.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={handleClose} title="Generate notes with AI">
      {error && <ErrorBanner message={error} />}
      {step === 'draft' ? (
        <form onSubmit={handleGenerate} className="space-y-4">
          <Input
            label="Topic"
            placeholder="e.g. The French Revolution"
            value={topic}
            onChange={(e) => setTopic(e.target.value)}
            required
          />
          <Input label="Subject (optional)" value={subject} onChange={(e) => setSubject(e.target.value)} />
          <Input
            label="Grade (optional)"
            placeholder="e.g. 9"
            value={grade}
            onChange={(e) => setGrade(e.target.value)}
          />
          <div className="flex gap-2 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={generating}>
              {generating ? 'Generating…' : 'Generate'}
            </Button>
          </div>
        </form>
      ) : (
        <div className="space-y-4">
          <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
          <Textarea label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={12} />
          <p className="text-xs text-slate">
            Review and edit the notes above — they're saved as a text file once you click "Save as study material".
          </p>
          <div className="flex gap-2 justify-between pt-2">
            <Button type="button" variant="secondary" onClick={() => setStep('draft')}>
              Back
            </Button>
            <div className="flex gap-2">
              <Button type="button" variant="secondary" onClick={handleClose}>
                Cancel
              </Button>
              <Button onClick={handleSave} disabled={saving}>
                {saving ? 'Saving…' : 'Save as study material'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </Modal>
  )
}
