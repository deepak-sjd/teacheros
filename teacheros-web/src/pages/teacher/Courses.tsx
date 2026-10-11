import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { Batch, Course, Page, SaveCourseRequest, TenantSettingsResponse } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Badge, Button, EmptyState, ErrorBanner, Input, Modal, Select, Skeleton, Textarea } from '@/components/ui'
import { formatCurrency } from '@/lib/format'

export function CoursesPage() {
  const [courses, setCourses] = useState<Course[] | null>(null)
  const [batches, setBatches] = useState<Batch[]>([])
  const [slug, setSlug] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Course | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  function load() {
    setError(null)
    api
      .get<Course[]>('/api/v1/courses')
      .then(setCourses)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load courses.'))
  }

  useEffect(() => {
    load()
    api.get<Page<Batch>>('/api/v1/batches', { size: 100 }).then((res) => setBatches(res.content)).catch(() => {})
    api.get<TenantSettingsResponse>('/api/v1/tenant').then((t) => setSlug(t.slug)).catch(() => {})
  }, [])

  const catalogueUrl = slug ? `${window.location.origin}/courses?school=${slug}` : null

  async function togglePublished(c: Course) {
    try {
      await api.patch(`/api/v1/courses/${c.id}/published`, { published: !c.published })
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this course.')
    }
  }

  async function handleDelete(c: Course) {
    if (!confirm(`Delete "${c.title}"? This cannot be undone.`)) return
    try {
      await api.del(`/api/v1/courses/${c.id}`)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete this course.')
    }
  }

  async function copyLink() {
    if (!catalogueUrl) return
    try {
      await navigator.clipboard.writeText(catalogueUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      /* clipboard unavailable - the link is still shown to copy by hand */
    }
  }

  return (
    <Layout>
      <PageHeader
        title="Courses"
        description="What you offer. Published courses appear on your public page, where visitors can send an enquiry."
        action={<Button onClick={() => setCreateOpen(true)}>New course</Button>}
      />

      {catalogueUrl && (
        <div className="flex flex-wrap items-center gap-2 mb-6 rounded-xl border border-line bg-white px-4 py-3">
          <span className="text-sm text-slate">Public page</span>
          <code className="flex-1 min-w-0 truncate text-xs text-ink bg-paper-dim rounded-md px-2.5 py-1.5">{catalogueUrl}</code>
          <Button variant="secondary" onClick={copyLink}>
            {copied ? 'Copied' : 'Copy link'}
          </Button>
          <a href={catalogueUrl} target="_blank" rel="noopener noreferrer">
            <Button variant="ghost" type="button">
              Open
            </Button>
          </a>
        </div>
      )}

      {error && <ErrorBanner message={error} />}

      {courses === null ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      ) : courses.length === 0 ? (
        <EmptyState
          title="No courses yet"
          description="Add your first course, then publish it to show it on your public page."
          action={<Button onClick={() => setCreateOpen(true)}>New course</Button>}
        />
      ) : (
        <div className="space-y-4">
          {courses.map((c) => (
            <div key={c.id} className="border border-line rounded-xl bg-white p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-ink text-base">{c.title}</p>
                    <Badge tone={c.published ? 'success' : 'neutral'}>{c.published ? 'Published' : 'Draft'}</Badge>
                    {c.demoVideoUrl && <Badge tone="brand">Demo video</Badge>}
                  </div>
                  <p className="text-xs text-slate-light mt-1">
                    {c.batchName ? `Enrols into ${c.batchName}` : 'No batch linked'}
                  </p>
                </div>
                <span className="font-display text-xl text-ink tabular-nums">
                  {c.price > 0 ? formatCurrency(c.price) : 'Free'}
                </span>
              </div>
              {c.description && <p className="text-sm text-slate mt-3 line-clamp-2">{c.description}</p>}
              <div className="flex flex-wrap items-center gap-2 mt-4">
                <Button variant="secondary" onClick={() => togglePublished(c)}>
                  {c.published ? 'Unpublish' : 'Publish'}
                </Button>
                <Link to={`/app/courses/${c.id}/content`}>
                  <Button variant="secondary" type="button">
                    Content
                  </Button>
                </Link>
                <Button variant="secondary" onClick={() => setEditing(c)}>
                  Edit
                </Button>
                <Button variant="ghost" onClick={() => handleDelete(c)} className="text-danger hover:text-danger">
                  Delete
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <CourseFormModal
        open={createOpen}
        batches={batches}
        onClose={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false)
          load()
        }}
      />
      <CourseFormModal
        open={editing !== null}
        course={editing}
        batches={batches}
        onClose={() => setEditing(null)}
        onSaved={() => {
          setEditing(null)
          load()
        }}
      />
    </Layout>
  )
}

function CourseFormModal({
  open,
  course,
  batches,
  onClose,
  onSaved,
}: {
  open: boolean
  course?: Course | null
  batches: Batch[]
  onClose: () => void
  onSaved: () => void
}) {
  const isEdit = !!course

  const [title, setTitle] = useState('')
  const [price, setPrice] = useState('0')
  const [description, setDescription] = useState('')
  const [syllabus, setSyllabus] = useState('')
  const [demoVideoUrl, setDemoVideoUrl] = useState('')
  const [batchId, setBatchId] = useState('')
  const [published, setPublished] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // The modal instance is reused, so re-sync the fields whenever it opens.
  useEffect(() => {
    if (!open) return
    setTitle(course?.title ?? '')
    setPrice(String(course?.price ?? 0))
    setDescription(course?.description ?? '')
    setSyllabus(course?.syllabus ?? '')
    setDemoVideoUrl(course?.demoVideoUrl ?? '')
    setBatchId(course?.batchId ?? '')
    setPublished(course?.published ?? false)
    setError(null)
    setSaving(false)
  }, [open, course])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      const body: SaveCourseRequest = {
        title,
        description: description || undefined,
        price: Number(price) || 0,
        syllabus: syllabus || undefined,
        demoVideoUrl: demoVideoUrl || undefined,
        batchId: batchId || undefined,
        published,
      }
      if (isEdit && course) {
        await api.put(`/api/v1/courses/${course.id}`, body)
      } else {
        await api.post('/api/v1/courses', body)
      }
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : `Could not ${isEdit ? 'update' : 'create'} the course.`)
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? 'Edit course' : 'New course'}>
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={255} />
        <Input
          label="Price (\u20b9)"
          type="number"
          min="0"
          step="0.01"
          value={price}
          onChange={(e) => setPrice(e.target.value)}
          required
        />
        <Textarea
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={4}
          placeholder="What students will learn and who it's for"
        />
        <Textarea
          label="Syllabus (one topic per line)"
          value={syllabus}
          onChange={(e) => setSyllabus(e.target.value)}
          rows={5}
          placeholder={'Algebra basics\nQuadratic equations\nGeometry'}
        />
        <Input
          label="Demo video link (optional)"
          type="url"
          value={demoVideoUrl}
          onChange={(e) => setDemoVideoUrl(e.target.value)}
          placeholder="https://www.youtube.com/watch?v=\u2026"
        />
        <Select label="Batch students join on enrolment" value={batchId} onChange={(e) => setBatchId(e.target.value)}>
          <option value="">No batch linked</option>
          {batches.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name}
            </option>
          ))}
        </Select>
        <label className="flex items-center gap-2.5 text-sm text-ink cursor-pointer">
          <input
            type="checkbox"
            checked={published}
            onChange={(e) => setPublished(e.target.checked)}
            className="h-4 w-4 accent-[var(--color-chalk-deep)]"
          />
          Published on the public page
        </label>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving\u2026' : isEdit ? 'Save changes' : 'Create course'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
