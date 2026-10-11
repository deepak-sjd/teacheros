import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError } from '@/lib/api'
import { getPublicSchoolSlug } from '@/lib/publicTenant'
import type { PublicCourseDetail as CourseDetail, PublicEnquiryRequest } from '@/lib/types'
import { Button, EmptyState, ErrorBanner, Input, Modal, Skeleton, Textarea } from '@/components/ui'
import { formatCurrency } from '@/lib/format'
import { PublicShell } from '@/pages/public/PublicShell'

/** YouTube links become an inline player; any other link falls back to a plain button. */
function toEmbedUrl(url: string): string | null {
  try {
    const u = new URL(url)
    const host = u.hostname.replace(/^www\./, '')
    let id: string | null = null
    if (host === 'youtu.be') id = u.pathname.slice(1)
    else if (host === 'youtube.com' || host === 'm.youtube.com') {
      if (u.pathname === '/watch') id = u.searchParams.get('v')
      else if (u.pathname.startsWith('/embed/')) id = u.pathname.split('/')[2] ?? null
    }
    return id && /^[\w-]{6,20}$/.test(id) ? `https://www.youtube.com/embed/${id}` : null
  } catch {
    return null
  }
}

export function PublicCourseDetail() {
  const { id } = useParams<{ id: string }>()
  const slug = getPublicSchoolSlug()
  const [course, setCourse] = useState<CourseDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [enquireOpen, setEnquireOpen] = useState(false)

  useEffect(() => {
    if (!id) return
    if (!slug) {
      setError('No school selected. Open the link your school shared with you.')
      return
    }
    setCourse(null)
    setError(null)
    api
      .get<CourseDetail>(`/api/v1/public/schools/${encodeURIComponent(slug)}/courses/${id}`)
      .then(setCourse)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this course.'))
  }, [id, slug])

  const topics = (course?.syllabus ?? '')
    .split('\n')
    .map((t) => t.trim())
    .filter(Boolean)
  const embed = course?.demoVideoUrl ? toEmbedUrl(course.demoVideoUrl) : null

  return (
    <PublicShell>
      {() => (
        <>
          <Link to="/courses" className="text-sm text-slate hover:text-ink underline underline-offset-2 mb-6 inline-block">
            &larr; All courses
          </Link>

          {error ? (
            <EmptyState
              title="Course not found"
              description={error}
              action={
                <Link to="/courses">
                  <Button variant="secondary">Browse courses</Button>
                </Link>
              }
            />
          ) : course === null ? (
            <div className="grid lg:grid-cols-3 gap-8">
              <div className="lg:col-span-2 space-y-4">
                <Skeleton className="h-10 w-2/3" />
                <Skeleton className="h-24" />
                <Skeleton className="h-40" />
              </div>
              <Skeleton className="h-48 rounded-xl" />
            </div>
          ) : (
            <div className="grid lg:grid-cols-3 gap-8 items-start">
              <div className="lg:col-span-2 space-y-8">
                <div>
                  <span className="block h-1 w-10 rounded-full bg-chalk mb-4" />
                  <h1 className="font-display text-3xl sm:text-4xl tracking-tight">{course.title}</h1>
                  {course.description && (
                    <p className="text-slate mt-4 whitespace-pre-wrap leading-relaxed">{course.description}</p>
                  )}
                </div>

                {course.demoVideoUrl && (
                  <section>
                    <h2 className="font-display text-xl mb-3">Watch a demo</h2>
                    {embed ? (
                      <div className="aspect-video overflow-hidden rounded-xl border border-line bg-black shadow-md">
                        <iframe
                          src={embed}
                          title={`${course.title} demo`}
                          className="h-full w-full"
                          loading="lazy"
                          allow="accelerometer; encrypted-media; picture-in-picture"
                          allowFullScreen
                        />
                      </div>
                    ) : (
                      <a href={course.demoVideoUrl} target="_blank" rel="noopener noreferrer">
                        <Button variant="secondary" type="button">
                          Open demo video
                        </Button>
                      </a>
                    )}
                  </section>
                )}

                {topics.length > 0 && (
                  <section>
                    <h2 className="font-display text-xl mb-3">What you&rsquo;ll cover</h2>
                    <ul className="space-y-2">
                      {topics.map((t, i) => (
                        <li key={i} className="flex items-start gap-3 rounded-lg border border-line bg-white px-4 py-3 text-sm">
                          <span className="mt-0.5 h-5 w-5 rounded-full bg-chalk-soft text-chalk-deep flex items-center justify-center shrink-0">
                            <svg viewBox="0 0 24 24" className="h-3 w-3" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                              <path d="M20 6 9 17l-5-5" />
                            </svg>
                          </span>
                          {t}
                        </li>
                      ))}
                    </ul>
                  </section>
                )}
              </div>

              <aside className="lg:sticky lg:top-24 rounded-xl border border-line bg-white p-6 shadow-md">
                <p className="text-xs uppercase tracking-wide text-slate-light">Course fee</p>
                <p className="font-display text-4xl text-ink mt-1 tabular-nums">
                  {course.price > 0 ? formatCurrency(course.price) : 'Free'}
                </p>
                <Button className="w-full mt-5" onClick={() => setEnquireOpen(true)}>
                  Enquire now
                </Button>
                <p className="text-xs text-slate-light mt-3 text-center">We&rsquo;ll call you back to answer questions.</p>
              </aside>
            </div>
          )}

          {course && (
            <EnquireModal
              open={enquireOpen}
              courseId={course.id}
              courseTitle={course.title}
              onClose={() => setEnquireOpen(false)}
            />
          )}
        </>
      )}
    </PublicShell>
  )
}

function EnquireModal({
  open,
  courseId,
  courseTitle,
  onClose,
}: {
  open: boolean
  courseId: string
  courseTitle: string
  onClose: () => void
}) {
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [website, setWebsite] = useState('') // honeypot - humans never see this
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    const schoolSlug = getPublicSchoolSlug()
    if (!schoolSlug) {
      setError('This form isn\u2019t set up yet. Please contact the school directly.')
      return
    }

    const body: PublicEnquiryRequest = {
      schoolSlug,
      name,
      phone,
      email: email || undefined,
      message: message || undefined,
      source: 'COURSE_PAGE',
      courseId,
      website,
    }

    setSubmitting(true)
    try {
      await api.post('/api/v1/public/enquiries', body, false)
      setSubmitted(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send your enquiry. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleClose() {
    onClose()
    // Reset after the close animation so a second visit starts clean.
    setTimeout(() => {
      setSubmitted(false)
      setError(null)
    }, 250)
  }

  return (
    <Modal open={open} onClose={handleClose} title={submitted ? 'Thanks!' : `Enquire about ${courseTitle}`}>
      {submitted ? (
        <div className="text-center py-2">
          <p className="text-slate text-sm">
            We&rsquo;ve got your details, {name.split(' ')[0] || 'there'}, and will get back to you shortly.
          </p>
          <Button className="mt-5" onClick={handleClose}>
            Done
          </Button>
        </div>
      ) : (
        <>
          {error && <ErrorBanner message={error} />}
          <form onSubmit={handleSubmit} className="relative space-y-4">
            <Input label="Your name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
            <Input
              label="Phone"
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+91 98765 43210"
              required
            />
            <Input label="Email (optional)" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            <Textarea
              label="Anything you'd like to ask? (optional)"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              maxLength={1800}
            />
            <input
              type="text"
              name="website"
              tabIndex={-1}
              autoComplete="off"
              aria-hidden
              value={website}
              onChange={(e) => setWebsite(e.target.value)}
              className="absolute -left-[9999px] h-0 w-0 opacity-0"
            />
            <div className="flex gap-2 justify-end pt-2">
              <Button type="button" variant="secondary" onClick={handleClose}>
                Cancel
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Sending\u2026' : 'Send enquiry'}
              </Button>
            </div>
          </form>
        </>
      )}
    </Modal>
  )
}
