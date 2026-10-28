import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Upload } from 'tus-js-client'
import { api, ApiError } from '@/lib/api'
import type { CreateVideoUploadResponse, VideoLibrary, VideoUpload, VideoUploadStatus } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Skeleton } from '@/components/ui'
import { formatDateTime } from '@/lib/format'

const POLL_MS = 5000

const STATUS_LABEL: Record<VideoUploadStatus, string> = {
  PENDING_UPLOAD: 'Waiting for file',
  PROCESSING: 'Processing',
  READY: 'Ready',
  FAILED: 'Failed',
}

const STATUS_STYLE: Record<VideoUploadStatus, string> = {
  PENDING_UPLOAD: 'bg-paper-dim text-slate',
  PROCESSING: 'bg-warn-soft text-warn',
  READY: 'bg-success-soft text-success',
  FAILED: 'bg-danger-soft text-danger',
}

function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`
  return `${Math.round(bytes / 1024 ** 2)} MB`
}

function formatDuration(seconds: number | null): string {
  if (!seconds) return ''
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

/**
 * Staff video library. The browser sends the file straight to the video
 * provider (TUS) using a one-way signature from our API; the video never
 * passes through this server. Processing status is polled until the video is
 * Ready, after which it can be chosen in a lesson.
 */
export function VideoLibraryPage() {
  const [library, setLibrary] = useState<VideoLibrary | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [starting, setStarting] = useState(false)
  const [uploading, setUploading] = useState<{ id: string; percent: number } | null>(null)
  const tusUpload = useRef<Upload | null>(null)

  function load() {
    api
      .get<VideoLibrary>('/api/v1/video-uploads')
      .then(setLibrary)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your videos.'))
  }

  useEffect(load, [])

  function merge(updated: VideoUpload) {
    setLibrary((prev) =>
      prev ? { ...prev, uploads: prev.uploads.map((u) => (u.id === updated.id ? updated : u)) } : prev,
    )
  }

  async function refresh(id: string) {
    try {
      merge(await api.post<VideoUpload>(`/api/v1/video-uploads/${id}/refresh`))
    } catch {
      // A failed status check is retried on the next tick; it must not spam errors.
    }
  }

  // Poll everything the provider is still working on, plus the upload this tab is sending.
  const inFlightKey = (library?.uploads ?? [])
    .filter((u) => u.status === 'PROCESSING' || u.id === uploading?.id)
    .map((u) => u.id)
    .join(',')
  useEffect(() => {
    if (!inFlightKey) return
    const timer = setInterval(() => {
      inFlightKey.split(',').forEach((id) => refresh(id))
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [inFlightKey])

  // Leaving mid-upload would cancel it.
  useEffect(() => {
    if (!uploading) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [uploading])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!file) return
    setError(null)
    setStarting(true)
    try {
      const res = await api.post<CreateVideoUploadResponse>('/api/v1/video-uploads', {
        title,
        fileSizeBytes: file.size,
      })
      setTitle('')
      setFile(null)
      load()

      const c = res.credentials
      if (!c) {
        // Dev provider: nothing to send. Just pick up the (instantly ready) status.
        await refresh(res.upload.id)
        return
      }

      setUploading({ id: res.upload.id, percent: 0 })
      const upload = new Upload(file, {
        endpoint: c.tusEndpoint,
        retryDelays: [0, 3000, 5000, 10000, 20000],
        headers: {
          AuthorizationSignature: c.authorizationSignature,
          AuthorizationExpire: String(c.authorizationExpire),
          VideoId: c.videoId,
          LibraryId: c.libraryId,
        },
        metadata: { filetype: file.type, title: res.upload.title },
        onProgress: (sent, total) => setUploading({ id: res.upload.id, percent: Math.round((sent / total) * 100) }),
        onError: () => {
          setUploading(null)
          setError('The upload was interrupted. Delete the entry below and try again.')
        },
        onSuccess: () => {
          setUploading(null)
          refresh(res.upload.id)
        },
      })
      tusUpload.current = upload
      upload.start()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start the upload.')
    } finally {
      setStarting(false)
    }
  }

  async function handleDelete(v: VideoUpload) {
    if (!confirm(`Delete "${v.title}" permanently? This cannot be undone.`)) return
    setError(null)
    try {
      await api.del(`/api/v1/video-uploads/${v.id}`)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete the video.')
    }
  }

  const pct = library && library.limitBytes > 0 ? Math.min(100, (library.usedBytes / library.limitBytes) * 100) : 0

  return (
    <Layout>
      <PageHeader
        title="Video library"
        description="Upload recorded lectures here, then pick them when you add a video lesson to a course."
      />

      {error && <ErrorBanner message={error} />}

      {library && (
        <div className="border border-line rounded-lg bg-white p-4 mb-6">
          <div className="flex items-center justify-between text-sm mb-2">
            <span className="text-ink font-medium">Storage</span>
            <span className="text-slate">
              {formatBytes(library.usedBytes)} of {formatBytes(library.limitBytes)} used
            </span>
          </div>
          <div className="h-2 rounded-full bg-paper-dim overflow-hidden">
            <div className={`h-full ${pct > 90 ? 'bg-danger' : 'bg-chalk'}`} style={{ width: `${pct}%` }} />
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="border border-line rounded-lg bg-white p-4 mb-6 space-y-4">
        <h2 className="font-display text-lg text-ink">Upload a video</h2>
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required disabled={!!uploading} />
        <label className="block">
          <span className="block text-sm font-medium text-ink mb-1.5">Video file</span>
          <input
            type="file"
            accept="video/*"
            disabled={!!uploading}
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="block w-full text-sm text-slate file:mr-3 file:rounded-md file:border-0 file:bg-paper-dim file:px-3 file:py-2 file:text-sm file:font-medium file:text-ink hover:file:bg-line"
            required
          />
        </label>
        {uploading && (
          <div>
            <div className="h-2 rounded-full bg-paper-dim overflow-hidden">
              <div className="h-full bg-chalk transition-all" style={{ width: `${uploading.percent}%` }} />
            </div>
            <p className="text-xs text-slate-light mt-1">Uploading {uploading.percent}% — keep this tab open.</p>
          </div>
        )}
        <div className="flex justify-end">
          <Button type="submit" disabled={!file || !title.trim() || starting || !!uploading}>
            {starting ? 'Starting…' : uploading ? 'Uploading…' : 'Upload'}
          </Button>
        </div>
      </form>

      {library === null ? (
        !error && <Skeleton className="h-32 rounded-lg" />
      ) : library.uploads.length === 0 ? (
        <EmptyState title="No videos yet" description="Upload your first recorded lecture above." />
      ) : (
        <div className="space-y-2">
          {library.uploads.map((v) => (
            <div key={v.id} className="border border-line rounded-lg bg-white p-4 flex items-start justify-between gap-4">
              <div className="min-w-0">
                <p className="font-medium text-ink truncate">{v.title}</p>
                <p className="text-xs text-slate-light mt-1">
                  {formatBytes(v.sizeBytes)}
                  {v.durationSeconds ? ` \u00b7 ${formatDuration(v.durationSeconds)}` : ''} {'\u00b7'}{' '}
                  {formatDateTime(v.createdAt)}
                </p>
                {v.status === 'FAILED' && v.failureReason && (
                  <p className="text-xs text-danger mt-1">{v.failureReason}</p>
                )}
              </div>
              <div className="flex items-center gap-3 whitespace-nowrap">
                <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${STATUS_STYLE[v.status]}`}>
                  {v.id === uploading?.id ? `Uploading ${uploading.percent}%` : STATUS_LABEL[v.status]}
                </span>
                {v.status === 'PENDING_UPLOAD' && v.id !== uploading?.id && (
                  <button onClick={() => refresh(v.id)} className="text-sm text-slate hover:text-ink underline underline-offset-2">
                    Check status
                  </button>
                )}
                {v.id !== uploading?.id && (
                  <button onClick={() => handleDelete(v)} className="text-sm text-danger hover:opacity-80 underline underline-offset-2">
                    Delete
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </Layout>
  )
}
