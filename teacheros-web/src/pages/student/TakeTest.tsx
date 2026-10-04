import { useEffect, useRef, useState } from 'react'
// Import line, add useSearchParams:
import { useSearchParams } from 'react-router-dom'   // ← new
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, EmptyState, ErrorBanner } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import type {
  Assessment,
  MyAttemptResponse,
  Page,
  QuestionForStudent,
  Student,
  StartTestResponse,
  SubmitResultResponse,
} from '@/lib/types'

interface TestWithStatus {
  test: Assessment
  status: MyAttemptResponse
}

function formatRemaining(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

function OnlineTestList({ student }: { student: Student }) {
  const [searchParams] = useSearchParams()                    // ← new
  const preselectedTestId = searchParams.get('testId')         // ← new
  const autoSelectedRef = useRef(false)                        // ← new
  const [items, setItems] = useState<TestWithStatus[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [active, setActive] = useState<Assessment | null>(null)

  function load() {
    if (!student.batchId) {
      setItems([])
      return
    }
    setError(null)
    api
      .get<Page<Assessment>>('/api/v1/tests', { batchId: student.batchId, testType: 'ONLINE_MCQ', size: 100 })
      .then(async (res) => {
        const withStatus = await Promise.all(
          res.content.map(async (test) => ({
            test,
            status: await api.get<MyAttemptResponse>(`/api/v1/tests/${test.id}/my-attempt`),
          })),
        )
        setItems(withStatus)
        // ↓ new block
        if (preselectedTestId && !autoSelectedRef.current) {
          const match = withStatus.find((it) => it.test.id === preselectedTestId && it.status.status !== 'SUBMITTED')
          if (match) {
            autoSelectedRef.current = true
            setActive(match.test)
          }
        }
        // ↑ new block
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load tests.'))
  }

  useEffect(load, [student.id, student.batchId])

  if (active) {
    return (
      <TakeTestScreen
        test={active}
        onDone={() => {
          setActive(null)
          load()
        }}
      />
    )
  }

  if (!student.batchId) {
    return <EmptyState title="No batch assigned" description="You'll see tests here once you're added to a batch." />
  }

  if (error) return <ErrorBanner message={error} />
  if (items === null) return <p className="text-slate text-sm">Loading…</p>
  if (items.length === 0) {
    return <EmptyState title="No online tests yet" description="Your teacher hasn't published one for your batch." />
  }

  return (
    <div className="space-y-2">
      {items.map(({ test, status }) => (
        <div key={test.id} className="border border-line rounded-lg bg-white p-4 flex items-center justify-between gap-4">
          <div>
            <p className="font-medium text-ink">{test.title}</p>
            <p className="text-xs text-slate mt-1">
              {test.subject ? `${test.subject} \u00b7 ` : ''}
              {test.durationMinutes ? `${test.durationMinutes} min \u00b7 ` : ''}
              Out of {test.maxMarks}
            </p>
          </div>
          {status.status === 'SUBMITTED' ? (
            <span className="text-sm font-medium text-ink whitespace-nowrap">
              Scored {status.score} / {status.maxMarks}
            </span>
          ) : (
            <Button onClick={() => setActive(test)}>{status.status === 'IN_PROGRESS' ? 'Continue' : 'Start test'}</Button>
          )}
        </div>
      ))}
    </div>
  )
}

/**
 * One screen, every question at once - simplest to build and to take on a
 * phone, rather than a paginated one-question-per-screen flow. The
 * deadline countdown is client-only (the server doesn't reject a late
 * submit); it auto-submits at zero so a forgotten tab doesn't just sit
 * open past the limit.
 */
function TakeTestScreen({ test, onDone }: { test: Assessment; onDone: () => void }) {
  const [session, setSession] = useState<StartTestResponse | null>(null)
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [remaining, setRemaining] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SubmitResultResponse | null>(null)
  const submittedRef = useRef(false)

  useEffect(() => {
    api
      .post<StartTestResponse>(`/api/v1/tests/${test.id}/start`)
      .then(setSession)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not start this test.'))
  }, [test.id])

  useEffect(() => {
    if (!session?.softDeadline) return
    const deadline = new Date(session.softDeadline).getTime()
    const tick = () => setRemaining(Math.max(0, Math.round((deadline - Date.now()) / 1000)))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [session?.softDeadline])

  useEffect(() => {
    if (remaining === 0 && !submittedRef.current) {
      submittedRef.current = true
      handleSubmit()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remaining])

  async function handleSubmit() {
    if (!session) return
    setError(null)
    setSubmitting(true)
    try {
      const res = await api.post<SubmitResultResponse>(`/api/v1/tests/${test.id}/submit`, {
        answers: session.questions.map((q) => ({ questionId: q.id, selectedOption: answers[q.id] || undefined })),
      })
      setResult(res)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit your answers.')
    } finally {
      setSubmitting(false)
    }
  }

  if (result) {
    return (
      <Layout>
        <PageHeader
          title={`Result: ${test.title}`}
          description={`You scored ${result.score} out of ${result.maxMarks}`}
          action={<Button onClick={onDone}>Back to tests</Button>}
        />
        <div className="space-y-2">
          {result.breakdown.map((r, i) => (
            <div key={r.questionId} className="border border-line rounded-lg bg-white p-4">
              <p className="text-sm text-ink font-medium">
                Q{i + 1}. {r.questionText}
              </p>
              <p className={`text-xs mt-2 ${r.correct ? 'text-success' : 'text-danger'}`}>
                Your answer: {r.selectedOption ?? 'Skipped'} {'\u00b7'} Correct answer: {r.correctOption} {'\u00b7'}{' '}
                {r.marksAwarded} / {r.marksPossible} marks
              </p>
            </div>
          ))}
        </div>
      </Layout>
    )
  }

  return (
    <Layout>
      <PageHeader
        title={test.title}
        description={remaining !== null ? `Time remaining: ${formatRemaining(remaining)}` : 'No time limit'}
        action={
          <Button variant="secondary" onClick={onDone}>
            Exit
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      {!session ? (
        <p className="text-slate text-sm">Loading…</p>
      ) : (
        <>
          <div className="space-y-4 mb-6">
            {session.questions.map((q: QuestionForStudent, i: number) => (
              <div key={q.id} className="border border-line rounded-lg bg-white p-4">
                <p className="text-sm font-medium text-ink mb-3">
                  Q{i + 1}. {q.questionText}
                  <span className="text-xs text-slate font-normal"> ({q.marks} marks)</span>
                </p>
                <div className="space-y-2">
                  {(['A', 'B', 'C', 'D'] as const).map((letter) => (
                    <label key={letter} className="flex items-center gap-2 text-sm text-ink cursor-pointer">
                      <input
                        type="radio"
                        name={q.id}
                        checked={answers[q.id] === letter}
                        onChange={() => setAnswers((prev) => ({ ...prev, [q.id]: letter }))}
                      />
                      <span>{q[`option${letter}` as 'optionA' | 'optionB' | 'optionC' | 'optionD']}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? 'Submitting…' : 'Submit test'}
          </Button>
        </>
      )}
    </Layout>
  )
}

export function StudentTakeTest() {
  return (
    <StudentSectionShell title="Take a test" description="Online tests available for your batch.">
      {(student) => <OnlineTestList student={student} />}
    </StudentSectionShell>
  )
}

