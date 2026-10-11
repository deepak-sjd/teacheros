import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner, Skeleton } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type {
  LearnCourse,
  LessonDetail,
  LessonSummary,
  PlaybackResponse,
  LessonProgressResponse,
} from '@/lib/types'

const SAVE_EVERY_MS = 10_000

export function StudentLearnCourse() {
  const { courseId } = useParams<{ courseId: string }>()
  const [course, setCourse] = useState<LearnCourse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [lessonId, setLessonId] = useState<string | null>(null)

  function load(selectFirst: boolean) {
    if (!courseId) return
    api
      .get<LearnCourse>(`/api/v1/learn/courses/${courseId}`)
      .then((c) => {
        setCourse(c)
        if (selectFirst) {
          // Resume at the first lesson not yet completed, else the first lesson.
          const all = c.chapters.flatMap((ch) => ch.lessons)
          setLessonId((all.find((l) => !l.completed) ?? all[0])?.id ?? null)
        }
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this course.'))
  }

  useEffect(() => load(true), [courseId])

  return (
    <Layout>
      <Link to="/app/learn" className="text-sm text-slate hover:text-ink underline underline-offset-2 mb-6 inline-block">
        ← My courses
      </Link>
      <PageHeader
        title={course?.title ?? 'Course'}
        description={course ? `${course.completedLessons} of ${course.totalLessons} lessons done` : undefined}
      />
      {error && <ErrorBanner message={error} />}

      {course === null ? (
        !error && <Skeleton className="h-64 rounded-lg" />
      ) : course.totalLessons === 0 ? (
        <EmptyState title="No lessons yet" description="Your teacher hasn't added lessons to this course." />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[18rem_1fr]">
          <nav className="space-y-4">
            {course.chapters.map((ch) => (
              <div key={ch.id}>
                <p className="text-sm font-medium text-slate mb-1.5">{ch.title}</p>
                <div className="space-y-1">
                  {ch.lessons.map((l: LessonSummary) => (
                    <button
                      key={l.id}
                      onClick={() => setLessonId(l.id)}
                      className={`w-full text-left rounded-md px-3 py-2 text-sm border transition-colors flex items-center gap-2 ${
                        lessonId === l.id ? 'bg-ink text-paper border-ink' : 'bg-white text-ink border-line hover:border-ink'
                      }`}
                    >
                      <span className={l.completed ? 'text-success' : 'text-slate-light'}>{l.completed ? '\u2713' : '\u25cb'}</span>
                      <span className="truncate">{l.title}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </nav>

          <div>{lessonId && <LessonView key={lessonId} lessonId={lessonId} onProgress={() => load(false)} />}</div>
        </div>
      )}
    </Layout>
  )
}

function LessonView({ lessonId, onProgress }: { lessonId: string; onProgress: () => void }) {
  const [lesson, setLesson] = useState<LessonDetail | null>(null)
  const [playbackUrl, setPlaybackUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [completed, setCompleted] = useState(false)
  const lastSavedAt = useRef(0)

  useEffect(() => {
    api
      .get<LessonDetail>(`/api/v1/learn/lessons/${lessonId}`)
      .then((l) => {
        setLesson(l)
        setCompleted(l.completed)
        if (l.type === 'VIDEO') {
          return api.get<PlaybackResponse>(`/api/v1/learn/lessons/${lessonId}/playback`).then((p) => setPlaybackUrl(p.url))
        }
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this lesson.'))
  }, [lessonId])

  // Progress saves are best-effort: a failed ping must never interrupt watching.
  function save(body: { positionSeconds?: number; completed?: boolean }, refresh = false) {
    api
      .put<LessonProgressResponse>(`/api/v1/learn/lessons/${lessonId}/progress`, body)
      .then((res) => {
        setCompleted(res.completed)
        if (refresh) onProgress()
      })
      .catch(() => {})
  }

  if (error) return <ErrorBanner message={error} />
  if (!lesson) return <Skeleton className="h-64 rounded-lg" />

  return (
    <div className="space-y-4">
      <h2 className="font-display text-xl text-ink">{lesson.title}</h2>

      {lesson.type === 'VIDEO' &&
        (playbackUrl ? (
          <video
            src={playbackUrl}
            controls
            className="w-full rounded-lg bg-ink"
            onLoadedMetadata={(e) => {
              // Resume where they left off (a finished lesson restarts from 0).
              if (!lesson.completed && lesson.positionSeconds > 0) e.currentTarget.currentTime = lesson.positionSeconds
            }}
            onTimeUpdate={(e) => {
              const now = Date.now()
              if (now - lastSavedAt.current >= SAVE_EVERY_MS) {
                lastSavedAt.current = now
                save({ positionSeconds: Math.floor(e.currentTarget.currentTime) })
              }
            }}
            onPause={(e) => save({ positionSeconds: Math.floor(e.currentTarget.currentTime) })}
            onEnded={(e) => save({ positionSeconds: Math.floor(e.currentTarget.currentTime), completed: true }, true)}
          />
        ) : (
          <Skeleton className="h-64 rounded-lg" />
        ))}

      {lesson.type === 'NOTES' && (
        <div className="rounded-lg border border-line bg-white p-4">
          <p className="text-sm text-ink whitespace-pre-wrap leading-relaxed">{lesson.notesText}</p>
        </div>
      )}

      {lesson.type === 'LINK' && lesson.linkUrl && (
        <a
          href={lesson.linkUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-block rounded-md bg-ink text-paper px-4 py-2 text-sm font-medium hover:bg-ink-light"
        >
          Open link
        </a>
      )}

      {lesson.type !== 'VIDEO' && (
        <div>
          <Button variant="secondary" onClick={() => save({ completed: !completed }, true)}>
            {completed ? 'Mark as not done' : 'Mark as done'}
          </Button>
        </div>
      )}
      {lesson.type === 'VIDEO' && completed && <p className="text-sm text-success">Lesson completed.</p>}
    </div>
  )
}
