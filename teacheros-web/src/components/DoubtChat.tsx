import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { AskDoubtResponse, DoubtMessage, DoubtThreadResponse } from '@/lib/types'
import { Button, ErrorBanner, Textarea } from '@/components/ui'
import { formatDateTime } from '@/lib/format'

const QUESTION_MAX_LENGTH = 1000

/**
 * One thread per student, same shape as MessageThread. Only the STUDENT
 * role can actually ask (see DoubtService#ask on the backend) - a PARENT
 * viewing their child's thread gets a read-only view instead of an input,
 * the mirror image of how MessageThread handles a STUDENT viewer.
 */
export function DoubtChat({ studentId }: { studentId: string }) {
  const { role } = useAuth()
  const canAsk = role === 'STUDENT'

  const [messages, setMessages] = useState<DoubtMessage[]>([])
  const [askedToday, setAskedToday] = useState(0)
  const [dailyLimit, setDailyLimit] = useState(30)
  const [loading, setLoading] = useState(true)
  const [question, setQuestion] = useState('')
  const [asking, setAsking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setLoading(true)
    api
      .get<DoubtThreadResponse>(`/api/v1/doubts/students/${studentId}`)
      .then((res) => {
        setMessages(res.messages)
        setAskedToday(res.questionsAskedToday)
        setDailyLimit(res.dailyQuestionLimit)
      })
      .finally(() => setLoading(false))
  }, [studentId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length, asking])

  async function handleAsk(e: FormEvent) {
    e.preventDefault()
    if (!question.trim()) return
    setError(null)
    setAsking(true)
    try {
      const res = await api.post<AskDoubtResponse>(`/api/v1/doubts/students/${studentId}/ask`, { question })
      setMessages((prev) => [...prev, res.question, res.answer])
      setAskedToday((prev) => prev + 1)
      setQuestion('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not get an answer. Please try again.')
    } finally {
      setAsking(false)
    }
  }

  const limitReached = askedToday >= dailyLimit

  return (
    <div className="rounded-lg border border-line bg-white">
      <div className="max-h-[28rem] overflow-y-auto p-4 space-y-3">
        {loading ? (
          <p className="text-slate text-sm">Loading…</p>
        ) : messages.length === 0 ? (
          <p className="text-slate text-sm text-center py-6">
            {canAsk ? 'No doubts asked yet. Type a question below to get started!' : 'No doubts asked yet.'}
          </p>
        ) : (
          messages.map((m) => (
            <div key={m.id} className={`flex ${m.sender === 'STUDENT' ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[75%] rounded-lg px-3 py-2 ${
                  m.sender === 'STUDENT' ? 'bg-ink text-paper' : 'bg-paper-dim text-ink'
                }`}
              >
                {m.sender === 'AI' && <p className="text-[11px] font-medium opacity-70 mb-0.5">AI Tutor</p>}
                <p className="text-sm whitespace-pre-wrap">{m.body}</p>
                <p className={`text-[10px] mt-1 ${m.sender === 'STUDENT' ? 'text-paper/60' : 'text-slate-light'}`}>
                  {formatDateTime(m.createdAt)}
                </p>
              </div>
            </div>
          ))
        )}
        {asking && (
          <div className="flex justify-start">
            <div className="max-w-[75%] rounded-lg px-3 py-2 bg-paper-dim text-ink">
              <p className="text-[11px] font-medium opacity-70 mb-0.5">AI Tutor</p>
              <p className="text-sm text-slate-light">Thinking…</p>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {error && (
        <div className="px-4">
          <ErrorBanner message={error} />
        </div>
      )}

      {canAsk ? (
        <form onSubmit={handleAsk} className="border-t border-line p-3 space-y-2">
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <Textarea
                rows={2}
                placeholder="Type your doubt here…"
                value={question}
                maxLength={QUESTION_MAX_LENGTH}
                onChange={(e) => setQuestion(e.target.value)}
                disabled={limitReached}
              />
            </div>
            <Button type="submit" disabled={asking || !question.trim() || limitReached}>
              {asking ? 'Asking…' : 'Ask'}
            </Button>
          </div>
          <p className="text-xs text-slate-light">
            {limitReached
              ? `You've reached today's limit of ${dailyLimit} questions. Try again tomorrow.`
              : `${askedToday} of ${dailyLimit} questions asked today.`}
          </p>
        </form>
      ) : (
        <div className="border-t border-line p-3">
          <p className="text-xs text-slate-light">
            Only your child can ask the AI tutor a new question - you can follow along here.
          </p>
        </div>
      )}
    </div>
  )
}
