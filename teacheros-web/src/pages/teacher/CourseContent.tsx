import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import type { LessonType, StaffChapter, StaffLesson } from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Input, Modal, Select, Skeleton, Textarea } from '@/components/ui'

const TYPE_LABEL: Record<LessonType, string> = { VIDEO: 'Video', NOTES: 'Notes', LINK: 'Link' }

/**
 * Staff authoring for one course: chapters, and lessons inside them.
 * Video lessons store a provider name + the provider's own reference, never
 * a file - the platform signs playback URLs for students on demand.
 */
export function CourseContent() {
  const { id } = useParams<{ id: string }>()
  const [title, setTitle] = useState('')
  const [chapters, setChapters] = useState<StaffChapter[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [chapterModal, setChapterModal] = useState<{ chapter: StaffChapter | null } | null>(null)
  const [lessonModal, setLessonModal] = useState<{ chapterId: string; lesson: StaffLesson | null } | null>(null)

  function load() {
    if (!id) return
    setError(null)
    api
      .get<StaffChapter[]>(`/api/v1/courses/${id}/chapters`)
      .then(setChapters)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the course content.'))
  }

  useEffect(() => {
    if (!id) return
    api.get<{ title: string }>(`/api/v1/courses/${id}`).then((c) => setTitle(c.title)).catch(() => {})
    load()
  }, [id])

  async function handleDeleteChapter(chapter: StaffChapter) {
    if (!confirm(`Delete "${chapter.title}" and all its lessons? Student progress on them is lost.`)) return
    await api.del(`/api/v1/chapters/${chapter.id}`)
    load()
  }

  async function handleDeleteLesson(lesson: StaffLesson) {
    if (!confirm(`Delete "${lesson.title}"? Student progress on it is lost.`)) return
    await api.del(`/api/v1/lessons/${lesson.id}`)
    load()
  }

  if (!id) return null

  return (
    <Layout>
      <Link to="/app/courses" className="text-sm text-slate hover:text-ink underline underline-offset-2 mb-6 inline-block">
        ← All courses
      </Link>
      <PageHeader
        title={title ? `Content: ${title}` : 'Course content'}
        description="Chapters and lessons. Only students in the course's batch can see published content."
        action={<Button onClick={() => setChapterModal({ chapter: null })}>New chapter</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {chapters === null ? (
        !error && <Skeleton className="h-32 rounded-lg" />
      ) : chapters.length === 0 ? (
        <EmptyState
          title="No chapters yet"
          description="Add a chapter, then add lessons to it."
          action={<Button onClick={() => setChapterModal({ chapter: null })}>New chapter</Button>}
        />
      ) : (
        <div className="space-y-4">
          {chapters.map((ch) => (
            <div key={ch.id} className="border border-line rounded-lg bg-white p-4">
              <div className="flex items-center justify-between gap-4 mb-3">
                <h3 className="font-display text-lg text-ink">{ch.title}</h3>
                <div className="flex items-center gap-3 whitespace-nowrap text-sm">
                  <button onClick={() => setLessonModal({ chapterId: ch.id, lesson: null })} className="text-slate hover:text-ink underline underline-offset-2">
                    Add lesson
                  </button>
                  <button onClick={() => setChapterModal({ chapter: ch })} className="text-slate hover:text-ink underline underline-offset-2">
                    Rename
                  </button>
                  <button onClick={() => handleDeleteChapter(ch)} className="text-danger hover:opacity-80 underline underline-offset-2">
                    Delete
                  </button>
                </div>
              </div>
              {ch.lessons.length === 0 ? (
                <p className="text-sm text-slate-light">No lessons in this chapter yet.</p>
              ) : (
                <div className="space-y-2">
                  {ch.lessons.map((l) => (
                    <div key={l.id} className="flex items-center justify-between gap-4 rounded-md border border-line px-3 py-2">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-ink truncate">{l.title}</p>
                        <p className="text-xs text-slate-light">
                          {TYPE_LABEL[l.type]}
                          {l.type === 'VIDEO' && l.videoProvider ? ` \u00b7 ${l.videoProvider}` : ''}
                          {l.durationSeconds ? ` \u00b7 ${Math.round(l.durationSeconds / 60)} min` : ''}
                        </p>
                      </div>
                      <div className="flex items-center gap-3 whitespace-nowrap text-sm">
                        <button onClick={() => setLessonModal({ chapterId: ch.id, lesson: l })} className="text-slate hover:text-ink underline underline-offset-2">
                          Edit
                        </button>
                        <button onClick={() => handleDeleteLesson(l)} className="text-danger hover:opacity-80 underline underline-offset-2">
                          Delete
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <ChapterModal
        courseId={id}
        state={chapterModal}
        onClose={() => setChapterModal(null)}
        onSaved={() => {
          setChapterModal(null)
          load()
        }}
      />
      <LessonModal
        state={lessonModal}
        onClose={() => setLessonModal(null)}
        onSaved={() => {
          setLessonModal(null)
          load()
        }}
      />
    </Layout>
  )
}

function ChapterModal({
  courseId,
  state,
  onClose,
  onSaved,
}: {
  courseId: string
  state: { chapter: StaffChapter | null } | null
  onClose: () => void
  onSaved: () => void
}) {
  const [title, setTitle] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (state) {
      setTitle(state.chapter?.title ?? '')
      setError(null)
    }
  }, [state])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      if (state?.chapter) await api.put(`/api/v1/chapters/${state.chapter.id}`, { title })
      else await api.post(`/api/v1/courses/${courseId}/chapters`, { title })
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the chapter.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={state !== null} onClose={onClose} title={state?.chapter ? 'Rename chapter' : 'New chapter'}>
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Chapter title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

function LessonModal({
  state,
  onClose,
  onSaved,
}: {
  state: { chapterId: string; lesson: StaffLesson | null } | null
  onClose: () => void
  onSaved: () => void
}) {
  const [title, setTitle] = useState('')
  const [type, setType] = useState<LessonType>('VIDEO')
  const [videoProvider, setVideoProvider] = useState('')
  const [videoRef, setVideoRef] = useState('')
  const [durationMinutes, setDurationMinutes] = useState('')
  const [notesText, setNotesText] = useState('')
  const [linkUrl, setLinkUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!state) return
    const l = state.lesson
    setTitle(l?.title ?? '')
    setType(l?.type ?? 'VIDEO')
    setVideoProvider(l?.videoProvider ?? '')
    setVideoRef(l?.videoRef ?? '')
    setDurationMinutes(l?.durationSeconds ? String(Math.round(l.durationSeconds / 60)) : '')
    setNotesText(l?.notesText ?? '')
    setLinkUrl(l?.linkUrl ?? '')
    setError(null)
  }, [state])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!state) return
    setError(null)
    setSaving(true)
    const body = {
      title,
      type,
      videoProvider: type === 'VIDEO' ? videoProvider || undefined : undefined,
      videoRef: type === 'VIDEO' ? videoRef || undefined : undefined,
      durationSeconds: type === 'VIDEO' && durationMinutes ? Math.round(Number(durationMinutes) * 60) : undefined,
      notesText: type === 'NOTES' ? notesText || undefined : undefined,
      linkUrl: type === 'LINK' ? linkUrl || undefined : undefined,
    }
    try {
      if (state.lesson) await api.put(`/api/v1/lessons/${state.lesson.id}`, body)
      else await api.post(`/api/v1/chapters/${state.chapterId}/lessons`, body)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the lesson.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={state !== null} onClose={onClose} title={state?.lesson ? 'Edit lesson' : 'New lesson'}>
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <Select label="Type" value={type} onChange={(e) => setType(e.target.value as LessonType)}>
          <option value="VIDEO">Video</option>
          <option value="NOTES">Notes</option>
          <option value="LINK">Link</option>
        </Select>

        {type === 'VIDEO' && (
          <>
            <Input label="Video provider" placeholder="e.g. mux, cloudflare, vimeo" value={videoProvider} onChange={(e) => setVideoProvider(e.target.value)} required />
            <Input label="Video reference" placeholder="The provider's playback/asset ID" value={videoRef} onChange={(e) => setVideoRef(e.target.value)} required />
            <Input label="Length in minutes (optional)" type="number" min="0" value={durationMinutes} onChange={(e) => setDurationMinutes(e.target.value)} />
            <p className="text-xs text-slate-light">Videos stay with your provider. Students only ever get a short-lived signed link.</p>
          </>
        )}
        {type === 'NOTES' && (
          <Textarea label="Notes" rows={8} value={notesText} onChange={(e) => setNotesText(e.target.value)} maxLength={20000} required />
        )}
        {type === 'LINK' && (
          <Input label="Link" type="url" placeholder="https://…" value={linkUrl} onChange={(e) => setLinkUrl(e.target.value)} required />
        )}

        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save lesson'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
