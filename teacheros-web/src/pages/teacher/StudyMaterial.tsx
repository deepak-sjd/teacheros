import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Batch, DownloadUrlResponse, Page, StudyMaterial } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select, Textarea } from '@/components/ui'
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
      .get<Page<StudyMaterial>>('/api/v1/study-material', { batchId: forBatchId, size: 100 })
      .then((res) => setItems(res.content))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (batchId) load(batchId)
  }, [batchId])

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
        action={batches.length > 0 ? <Button onClick={() => setUploadOpen(true)}>Upload file</Button> : undefined}
      />

      {batches.length === 0 ? (
        <EmptyState title="No batches yet" description="Create a batch first before uploading material." />
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
          ) : items.length === 0 ? (
            <EmptyState
              title="No files for this batch"
              description="Upload notes, PDFs, or reference material for students to access."
              action={<Button onClick={() => setUploadOpen(true)}>Upload file</Button>}
            />
          ) : (
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
