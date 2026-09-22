import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { Message } from '@/lib/types'
import { Button, ErrorBanner, Textarea } from '@/components/ui'
import { formatDateTime } from '@/lib/format'

/**
 * One thread per student, shared by MyChildren (parent), MyAttendance
 * (student, read-only - see the 403 note below), and StudentDetail (staff).
 * `mine` on each message is already resolved server-side per the caller's
 * role, so this component doesn't need to know who's viewing it.
 */
export function MessageThread({ studentId }: { studentId: string }) {
  const [messages, setMessages] = useState<Message[]>([])
  const [loading, setLoading] = useState(true)
  const [body, setBody] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setLoading(true)
    api
      .get<Message[]>(`/api/v1/messages/students/${studentId}`)
      .then(setMessages)
      .finally(() => setLoading(false))
    // Opening the thread marks it read for "my side" - fire-and-forget,
    // a failed read receipt shouldn't block viewing the thread itself.
    api.patch(`/api/v1/messages/students/${studentId}/read`).catch(() => {})
  }, [studentId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  async function handleSend(e: FormEvent) {
    e.preventDefault()
    if (!body.trim()) return
    setError(null)
    setSending(true)
    try {
      const sent = await api.post<Message>(`/api/v1/messages/students/${studentId}`, { body })
      setMessages((prev) => [...prev, sent])
      setBody('')
    } catch (err) {
      // A STUDENT viewer hits this as a 403 the first time they try to
      // send - the backend allows them to read the thread but not post to
      // it (only PARENT/staff can send). ApiError.message carries that
      // through to here as plain text rather than a generic failure.
      setError(err instanceof ApiError ? err.message : 'Could not send message.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="rounded-lg border border-line bg-white">
      <div className="max-h-80 overflow-y-auto p-4 space-y-3">
        {loading ? (
          <p className="text-slate text-sm">Loading…</p>
        ) : messages.length === 0 ? (
          <p className="text-slate text-sm text-center py-6">No messages yet. Say hello!</p>
        ) : (
          messages.map((m) => (
            <div key={m.id} className={`flex ${m.mine ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[75%] rounded-lg px-3 py-2 ${
                  m.mine ? 'bg-ink text-paper' : 'bg-paper-dim text-ink'
                }`}
              >
                {!m.mine && <p className="text-[11px] font-medium opacity-70 mb-0.5">{m.senderName}</p>}
                <p className="text-sm whitespace-pre-wrap">{m.body}</p>
                <p className={`text-[10px] mt-1 ${m.mine ? 'text-paper/60' : 'text-slate-light'}`}>
                  {formatDateTime(m.createdAt)}
                </p>
              </div>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>

      {error && (
        <div className="px-4">
          <ErrorBanner message={error} />
        </div>
      )}

      <form onSubmit={handleSend} className="flex items-end gap-2 border-t border-line p-3">
        <div className="flex-1">
          <Textarea
            rows={1}
            placeholder="Write a message…"
            value={body}
            onChange={(e) => setBody(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={sending || !body.trim()}>
          {sending ? 'Sending…' : 'Send'}
        </Button>
      </form>
    </div>
  )
}
