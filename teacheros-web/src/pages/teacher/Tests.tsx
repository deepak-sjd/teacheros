import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type {
  Assessment,
  Batch,
  GenerateQuestionsResponse,
  MarkResponse,
  Page,
  QuestionInput,
  QuestionResponse,
  Student,
  StudentResultRow,
  TestResultsResponse,
  TestType,
} from '@/lib/types'
import { Layout, PageHeader } from '@/components/Layout'
import {
  Button,
  Cell,
  EmptyState,
  ErrorBanner,
  Input,
  Ledger,
  Modal,
  Row,
  Select,
  Skeleton,
  StatBlock,
  Textarea,
} from '@/components/ui'
import { Pagination, PAGE_SIZE } from '@/components/Pagination'
import { formatDate, formatDateTime, todayIso } from '@/lib/format'

export function TestsPage() {
  const [batches, setBatches] = useState<Batch[]>([])
  const [batchId, setBatchId] = useState('')
  const [tests, setTests] = useState<Assessment[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [grading, setGrading] = useState<Assessment | null>(null)
  const [editingQuestions, setEditingQuestions] = useState<Assessment | null>(null)
  const [viewingResults, setViewingResults] = useState<Assessment | null>(null)
  const [page, setPage] = useState(0)
  const [pageMeta, setPageMeta] = useState<Page<Assessment> | null>(null)

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
      .get<Page<Assessment>>('/api/v1/tests', { batchId: forBatchId, page: p, size: PAGE_SIZE })
      .then((res) => {
        // Last item on the page was deleted: step back one page (effect will refetch)
        if (res.content.length === 0 && p > 0) {
          setPage(p - 1)
          return
        }
        setTests(res.content)
        setPageMeta(res)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (batchId) load(batchId, page)
  }, [batchId, page])

  async function handleDelete(id: string) {
    if (!confirm('Delete this test and all its marks? This cannot be undone.')) return
    await api.del(`/api/v1/tests/${id}`)
    load(batchId)
  }

  if (grading) {
    return <GradingScreen test={grading} onBack={() => setGrading(null)} />
  }

  if (viewingResults) {
    return <ResultsScreen test={viewingResults} onBack={() => setViewingResults(null)} />
  }

  if (editingQuestions) {
    return (
      <QuestionsEditor
        test={editingQuestions}
        onBack={() => setEditingQuestions(null)}
        onSaved={() => load(batchId)}
      />
    )
  }

  return (
    <Layout>
      <PageHeader
        title="Tests"
        description="Create a test for a batch, then grade every student in one sitting."
        action={batches.length > 0 ? <Button onClick={() => setCreateOpen(true)}>New test</Button> : undefined}
      />

      {batches.length === 0 ? (
        <EmptyState title="No batches yet" description="Create a batch first before adding a test." />
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
          ) : tests.length === 0 ? (
            <EmptyState
              title="No tests for this batch"
              description="Create a test, then grade students once it's done."
              action={<Button onClick={() => setCreateOpen(true)}>New test</Button>}
            />
          ) : (
            <>
              <div className="space-y-2">
                {tests.map((t) => (
                  <div key={t.id} className="border border-line rounded-lg bg-white p-4">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <p className="font-medium text-ink">{t.title}</p>
                          {t.testType === 'ONLINE_MCQ' && (
                            <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-paper-dim text-slate">
                              Online (MCQ)
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-slate mt-1">
                          {t.subject ? `${t.subject} \u00b7 ` : ''}
                          Out of {t.maxMarks} {'\u00b7'} {formatDate(t.testDate)}
                          {t.testType === 'ONLINE_MCQ' && t.durationMinutes ? ` \u00b7 ${t.durationMinutes} min` : ''}
                        </p>
                      </div>
                      <div className="flex items-center gap-3 whitespace-nowrap">
                        {t.testType === 'ONLINE_MCQ' && (
                          <button
                            onClick={() => setEditingQuestions(t)}
                            className="text-sm text-slate hover:text-ink underline underline-offset-2"
                          >
                            Questions
                          </button>
                        )}
                        <button
                          onClick={() => (t.testType === 'ONLINE_MCQ' ? setViewingResults(t) : setGrading(t))}
                          className="text-sm text-slate hover:text-ink underline underline-offset-2"
                        >
                          {t.testType === 'ONLINE_MCQ' ? 'Results' : 'Grade'}
                        </button>
                        <button
                          onClick={() => handleDelete(t.id)}
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

      <CreateTestModal
        open={createOpen}
        batchId={batchId}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          load(batchId)
        }}
      />
    </Layout>
  )
}

function CreateTestModal({
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
  const [subject, setSubject] = useState('')
  const [maxMarks, setMaxMarks] = useState('100')
  const [testDate, setTestDate] = useState(todayIso())
  const [testType, setTestType] = useState<TestType>('OFFLINE')
  const [durationMinutes, setDurationMinutes] = useState('')
  const [availableFrom, setAvailableFrom] = useState('')
  const [availableUntil, setAvailableUntil] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    const parsedMax = Number(maxMarks)
    if (!title.trim() || !parsedMax || parsedMax <= 0) {
      setError('Enter a title and a max marks value above 0.')
      return
    }

    setSaving(true)
    try {
      await api.post('/api/v1/tests', {
        batchId,
        title,
        subject: subject || undefined,
        maxMarks: parsedMax,
        testDate,
        testType,
        durationMinutes: testType === 'ONLINE_MCQ' && durationMinutes ? Number(durationMinutes) : undefined,
        availableFrom: testType === 'ONLINE_MCQ' && availableFrom ? new Date(availableFrom).toISOString() : undefined,
        availableUntil: testType === 'ONLINE_MCQ' && availableUntil ? new Date(availableUntil).toISOString() : undefined,
      })
      setTitle('')
      setSubject('')
      setMaxMarks('100')
      setTestType('OFFLINE')
      setDurationMinutes('')
      setAvailableFrom('')
      setAvailableUntil('')
      onCreated()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this test.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="New test">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Select label="Type" value={testType} onChange={(e) => setTestType(e.target.value as TestType)}>
          <option value="OFFLINE">Offline — I'll enter marks myself</option>
          <option value="ONLINE_MCQ">Online (MCQ) — students take it in the app</option>
        </Select>
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <Input label="Subject (optional)" value={subject} onChange={(e) => setSubject(e.target.value)} />
        <Input
          label={testType === 'ONLINE_MCQ' ? 'Max marks (placeholder — set by your questions)' : 'Max marks'}
          type="number"
          min="1"
          step="0.5"
          value={maxMarks}
          onChange={(e) => setMaxMarks(e.target.value)}
          required
        />
        <Input label="Test date" type="date" value={testDate} onChange={(e) => setTestDate(e.target.value)} required />
        {testType === 'ONLINE_MCQ' && (
          <>
            <Input
              label="Time limit in minutes (optional)"
              type="number"
              min="1"
              placeholder="No limit"
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(e.target.value)}
            />
            <Input
              label="Opens at (optional)"
              type="datetime-local"
              value={availableFrom}
              onChange={(e) => setAvailableFrom(e.target.value)}
            />
            <Input
              label="Closes at (optional)"
              type="datetime-local"
              value={availableUntil}
              onChange={(e) => setAvailableUntil(e.target.value)}
            />
            <p className="text-xs text-slate">
              Add your questions right after creating the test — it won't show up for students until it has at
              least one.
            </p>
          </>
        )}
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create test'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * Grade every student in the batch in one sitting, then submit once - the
 * same "whole class, one submission" workflow the mobile attendance screen
 * uses, not one API call per student.
 */
function GradingScreen({ test, onBack }: { test: Assessment; onBack: () => void }) {
  const [students, setStudents] = useState<Student[]>([])
  const [scores, setScores] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    setLoading(true)
    Promise.all([
      api.get<Page<Student>>('/api/v1/students', { batchId: test.batchId, size: 200 }),
      api.get<MarkResponse[]>(`/api/v1/tests/${test.id}/marks`),
    ])
      .then(([studentsRes, marksRes]) => {
        setStudents(studentsRes.content)
        const initial: Record<string, string> = {}
        for (const m of marksRes) {
          if (m.marksObtained !== null) initial[m.studentId] = String(m.marksObtained)
        }
        setScores(initial)
      })
      .finally(() => setLoading(false))
  }, [test.id, test.batchId])

  async function handleSubmit() {
    setError(null)
    setSaving(true)
    try {
      const entries = students.map((s) => ({
        studentId: s.id,
        marksObtained: scores[s.id]?.trim() ? Number(scores[s.id]) : null,
      }))
      await api.post(`/api/v1/tests/${test.id}/marks`, { entries })
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save marks. Check for values above max marks.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <PageHeader
        title={`Grade: ${test.title}`}
        description={`Out of ${test.maxMarks} \u00b7 ${formatDate(test.testDate)}`}
        action={
          <Button variant="secondary" onClick={onBack}>
            Back to tests
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <div className="space-y-2 mb-6">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 rounded-lg" />
          ))}
        </div>
      ) : students.length === 0 ? (
        <EmptyState title="No students in this batch" description="Add students to this batch first." />
      ) : saved ? (
        <EmptyState title="Marks saved" description={`Grades recorded for ${students.length} students.`} />
      ) : (
        <>
          <div className="space-y-2 mb-6">
            {students.map((s) => (
              <div key={s.id} className="flex items-center justify-between gap-4 border border-line rounded-lg bg-white p-3">
                <span className="text-sm text-ink">{s.fullName}</span>
                <input
                  type="number"
                  min="0"
                  max={test.maxMarks}
                  step="0.5"
                  placeholder="Not graded"
                  value={scores[s.id] ?? ''}
                  onChange={(e) => setScores((prev) => ({ ...prev, [s.id]: e.target.value }))}
                  className="w-28 rounded-md border border-line px-3 py-1.5 text-sm text-right focus:border-ink focus:ring-1 focus:ring-ink outline-none"
                />
              </div>
            ))}
          </div>
          <Button onClick={handleSubmit} disabled={saving}>
            {saving ? 'Saving…' : 'Save marks'}
          </Button>
        </>
      )}
    </Layout>
  )
}

/**
 * Teacher-facing results for an ONLINE_MCQ test - GET /{id}/results only
 * returns a row for students who have a TestAttempt (started or
 * submitted), so this merges that against the full batch roster the same
 * way student/Tests.tsx's OnlineTestsSection merges tests against
 * my-attempt: a roster student with no row here is "not started".
 */
function ResultsScreen({ test, onBack }: { test: Assessment; onBack: () => void }) {
  const [students, setStudents] = useState<Student[] | null>(null)
  const [results, setResults] = useState<TestResultsResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setStudents(null)
    setResults(null)
    setError(null)
    Promise.all([
      api.get<Page<Student>>('/api/v1/students', { batchId: test.batchId, size: 200 }),
      api.get<TestResultsResponse>(`/api/v1/tests/${test.id}/results`),
    ])
      .then(([studentsRes, resultsRes]) => {
        setStudents(studentsRes.content)
        setResults(resultsRes)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load results.'))
  }, [test.id, test.batchId])

  const loading = students === null || results === null

  const rows = loading
    ? []
    : students
        .map((s) => ({ student: s, result: results.students.find((r) => r.studentId === s.id) ?? null }))
        .sort((a, b) => {
          // Submitted (highest score first), then in-progress, then not
          // started - a teacher scanning this wants to see who's done
          // first, not an alphabetical roster.
          const rank = (r: StudentResultRow | null) => (r?.status === 'SUBMITTED' ? 0 : r ? 1 : 2)
          const rankDiff = rank(a.result) - rank(b.result)
          if (rankDiff !== 0) return rankDiff
          return (b.result?.score ?? -1) - (a.result?.score ?? -1)
        })

  const submittedCount = loading ? 0 : results.students.filter((r) => r.status === 'SUBMITTED').length
  const submittedScores = loading ? [] : results.students.filter((r) => r.status === 'SUBMITTED' && r.score !== null).map((r) => r.score!)
  const averageScore = submittedScores.length > 0 ? submittedScores.reduce((sum, v) => sum + v, 0) / submittedScores.length : null

  return (
    <Layout>
      <PageHeader
        title={`Results: ${test.title}`}
        description={loading ? undefined : `Out of ${results.maxMarks}`}
        action={
          <Button variant="secondary" onClick={onBack}>
            Back to tests
          </Button>
        }
      />

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Skeleton className="h-20 rounded-lg" />
            <Skeleton className="h-20 rounded-lg" />
            <Skeleton className="h-20 rounded-lg" />
          </div>
          <Skeleton className="h-64 rounded-lg" />
        </div>
      ) : students.length === 0 ? (
        <EmptyState title="No students in this batch" description="Add students to this batch first." />
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
            <StatBlock label="Submitted" value={`${submittedCount} of ${students.length}`} />
            <StatBlock
              label="Average score"
              value={averageScore !== null ? `${averageScore.toFixed(1)} / ${results.maxMarks}` : '\u2014'}
            />
            <StatBlock label="Questions" value={String(results.questions.length)} />
          </div>

          <h3 className="font-display text-xl text-ink mb-3">By student</h3>
          <Ledger columns={['Student', 'Status', 'Submitted', 'Score']}>
            {rows.map(({ student, result }) => (
              <Row key={student.id}>
                <Cell>{student.fullName}</Cell>
                <Cell>
                  {result?.status === 'SUBMITTED' ? 'Submitted' : result?.status === 'IN_PROGRESS' ? 'In progress' : 'Not started'}
                </Cell>
                <Cell>{result?.submittedAt ? formatDateTime(result.submittedAt) : '\u2014'}</Cell>
                <Cell className="font-medium">
                  {result?.status === 'SUBMITTED' ? `${result.score} / ${result.maxMarks}` : '\u2014'}
                </Cell>
              </Row>
            ))}
          </Ledger>

          <h3 className="font-display text-xl text-ink mt-8 mb-3">By question</h3>
          {results.questions.length === 0 ? (
            <EmptyState title="No questions" description="This test has no questions yet." />
          ) : (
            <div className="space-y-2">
              {results.questions.map((q, i) => {
                const answered = q.correctCount + q.incorrectCount
                const total = answered + q.unansweredCount
                const correctPct = total > 0 ? Math.round((q.correctCount / total) * 100) : 0
                return (
                  <div key={q.questionId} className="border border-line rounded-lg bg-white p-4">
                    <p className="text-sm text-ink mb-2">
                      <span className="text-slate-light">Q{i + 1}.</span> {q.questionText}
                    </p>
                    <div className="h-2 rounded-full bg-paper-dim overflow-hidden mb-2">
                      <div className="h-full bg-chalk" style={{ width: `${correctPct}%` }} />
                    </div>
                    <p className="text-xs text-slate-light">
                      {q.correctCount} correct {'\u00b7'} {q.incorrectCount} incorrect {'\u00b7'} {q.unansweredCount}{' '}
                      unanswered
                      {q.mostCommonWrongOption && (
                        <>
                          {' '}
                          {'\u00b7'} most picked wrong answer: option {q.mostCommonWrongOption}
                        </>
                      )}
                    </p>
                  </div>
                )
              })}
            </div>
          )}
        </>
      )}
    </Layout>
  )
}

const BLANK_QUESTION: QuestionInput = {
  questionText: '',
  optionA: '',
  optionB: '',
  optionC: '',
  optionD: '',
  correctOption: 'A',
  marks: 1,
}

/**
 * Full-replace question authoring - matches GradingScreen's "edit
 * everything, save once" pattern rather than one API call per question.
 * Saving recomputes the test's max marks server-side as the sum of these
 * questions' marks.
 */
function QuestionsEditor({
  test,
  onBack,
  onSaved,
}: {
  test: Assessment
  onBack: () => void
  onSaved: () => void
}) {
  const [questions, setQuestions] = useState<QuestionInput[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [generateOpen, setGenerateOpen] = useState(false)

  // If the list is still just the single untouched starter question,
  // the AI's drafts replace it outright; otherwise they're appended so a
  // teacher can generate a second batch without losing questions they've
  // already written or edited.
  function handleGenerated(drafts: QuestionInput[]) {
    setQuestions((prev) =>
      prev.length === 1 && JSON.stringify(prev[0]) === JSON.stringify(BLANK_QUESTION) ? drafts : [...prev, ...drafts],
    )
    setSaved(false)
  }

  useEffect(() => {
    api
      .get<QuestionResponse[]>(`/api/v1/tests/${test.id}/questions`)
      .then((res) =>
        setQuestions(
          res.length > 0
            ? res
                .sort((a, b) => a.position - b.position)
                .map(({ questionText, optionA, optionB, optionC, optionD, correctOption, marks }) => ({
                  questionText,
                  optionA,
                  optionB,
                  optionC,
                  optionD,
                  correctOption,
                  marks,
                }))
            : [{ ...BLANK_QUESTION }],
        ),
      )
      .finally(() => setLoading(false))
  }, [test.id])

  function updateQuestion(index: number, patch: Partial<QuestionInput>) {
    setQuestions((prev) => prev.map((q, i) => (i === index ? { ...q, ...patch } : q)))
  }

  function addQuestion() {
    setQuestions((prev) => [...prev, { ...BLANK_QUESTION }])
  }

  function removeQuestion(index: number) {
    setQuestions((prev) => prev.filter((_, i) => i !== index))
  }

  const totalMarks = questions.reduce((sum, q) => sum + (Number(q.marks) || 0), 0)

  async function handleSave() {
    setError(null)
    for (const [i, q] of questions.entries()) {
      if (!q.questionText.trim() || !q.optionA.trim() || !q.optionB.trim() || !q.optionC.trim() || !q.optionD.trim()) {
        setError(`Question ${i + 1} is missing text or an option.`)
        return
      }
      if (!Number(q.marks) || Number(q.marks) <= 0) {
        setError(`Question ${i + 1} needs marks above 0.`)
        return
      }
    }

    setSaving(true)
    try {
      await api.put(`/api/v1/tests/${test.id}/questions`, { questions })
      setSaved(true)
      onSaved()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save these questions.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Layout>
      <PageHeader
        title={`Questions: ${test.title}`}
        description={`${questions.length} question${questions.length === 1 ? '' : 's'} \u00b7 ${totalMarks} marks total`}
        action={
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setGenerateOpen(true)}>
              Generate with AI
            </Button>
            <Button variant="secondary" onClick={onBack}>
              Back to tests
            </Button>
          </div>
        }
      />

      {error && <ErrorBanner message={error} />}
      {saved && (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
          Saved. Max marks for this test is now {totalMarks}.
        </div>
      )}

      {loading ? (
        <div className="space-y-4 mb-6">
          {Array.from({ length: 2 }).map((_, i) => (
            <Skeleton key={i} className="h-64 rounded-lg" />
          ))}
        </div>
      ) : (
        <>
          <div className="space-y-4 mb-6">
            {questions.map((q, i) => (
              <div key={i} className="border border-line rounded-lg bg-white p-4 space-y-3">
                <div className="flex items-start justify-between gap-4">
                  <span className="text-xs font-medium text-slate mt-2 shrink-0">Q{i + 1}</span>
                  {questions.length > 1 && (
                    <button
                      onClick={() => removeQuestion(i)}
                      className="text-xs text-danger hover:opacity-80 underline underline-offset-2"
                    >
                      Remove
                    </button>
                  )}
                </div>
                <Textarea
                  label="Question"
                  value={q.questionText}
                  onChange={(e) => updateQuestion(i, { questionText: e.target.value })}
                  rows={2}
                />
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {(['A', 'B', 'C', 'D'] as const).map((letter) => (
                    <Input
                      key={letter}
                      label={`Option ${letter}`}
                      value={q[`option${letter}` as 'optionA' | 'optionB' | 'optionC' | 'optionD']}
                      onChange={(e) => updateQuestion(i, { [`option${letter}`]: e.target.value } as Partial<QuestionInput>)}
                    />
                  ))}
                </div>
                <div className="flex gap-3 items-end">
                  <div className="w-32">
                    <Select
                      label="Correct answer"
                      value={q.correctOption}
                      onChange={(e) => updateQuestion(i, { correctOption: e.target.value as QuestionInput['correctOption'] })}
                    >
                      <option value="A">A</option>
                      <option value="B">B</option>
                      <option value="C">C</option>
                      <option value="D">D</option>
                    </Select>
                  </div>
                  <div className="w-28">
                    <Input
                      label="Marks"
                      type="number"
                      min="0.5"
                      step="0.5"
                      value={q.marks}
                      onChange={(e) => updateQuestion(i, { marks: Number(e.target.value) })}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div className="flex gap-3">
            <Button variant="secondary" onClick={addQuestion}>
              Add question
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? 'Saving…' : 'Save questions'}
            </Button>
          </div>
        </>
      )}

      <GenerateQuestionsModal
        open={generateOpen}
        defaultSubject={test.subject ?? ''}
        onClose={() => setGenerateOpen(false)}
        onGenerated={(drafts) => {
          setGenerateOpen(false)
          handleGenerated(drafts)
        }}
      />
    </Layout>
  )
}

/**
 * Drafts, not a direct save - the generated questions land in
 * QuestionsEditor's own editable list (same as a blank question added by
 * hand) so a teacher reviews/edits/removes any of them before "Save
 * questions" ever calls the API. Nothing from this modal is persisted by
 * itself.
 */
function GenerateQuestionsModal({
  open,
  defaultSubject,
  onClose,
  onGenerated,
}: {
  open: boolean
  defaultSubject: string
  onClose: () => void
  onGenerated: (drafts: QuestionInput[]) => void
}) {
  const [topic, setTopic] = useState('')
  const [subject, setSubject] = useState(defaultSubject)
  const [grade, setGrade] = useState('')
  const [difficulty, setDifficulty] = useState('medium')
  const [count, setCount] = useState('5')
  const [error, setError] = useState<string | null>(null)
  const [generating, setGenerating] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (!topic.trim()) {
      setError('Enter a topic to generate questions about.')
      return
    }
    const parsedCount = Math.max(1, Math.min(Number(count) || 5, 20))

    setGenerating(true)
    try {
      const res = await api.post<GenerateQuestionsResponse>('/api/v1/content-generator/questions', {
        topic,
        subject: subject || undefined,
        grade: grade || undefined,
        difficulty,
        count: parsedCount,
      })
      onGenerated(res.questions)
      setTopic('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not generate questions. Please try again.')
    } finally {
      setGenerating(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Generate questions with AI">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input
          label="Topic"
          placeholder="e.g. Quadratic equations"
          value={topic}
          onChange={(e) => setTopic(e.target.value)}
          required
        />
        <Input label="Subject (optional)" value={subject} onChange={(e) => setSubject(e.target.value)} />
        <Input
          label="Grade (optional)"
          placeholder="e.g. 10"
          value={grade}
          onChange={(e) => setGrade(e.target.value)}
        />
        <div className="grid grid-cols-2 gap-3">
          <Select label="Difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
            <option value="easy">Easy</option>
            <option value="medium">Medium</option>
            <option value="hard">Hard</option>
          </Select>
          <Input
            label="Number of questions"
            type="number"
            min="1"
            max="20"
            value={count}
            onChange={(e) => setCount(e.target.value)}
          />
        </div>
        <p className="text-xs text-slate">
          You'll be able to review and edit every question before saving - nothing is added to the test until you
          click "Save questions".
        </p>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={generating}>
            {generating ? 'Generating…' : 'Generate'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
