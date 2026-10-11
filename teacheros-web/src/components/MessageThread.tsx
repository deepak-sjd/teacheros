import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { Message } from '@/lib/types'
import { Button, ErrorBanner, Textarea } from '@/components/ui'
import { formatDateTime } from '@/lib/format'

/**
 * One thread per student, shared by MyChildren (parent), the student
 * Messages page (read-only), and StudentDetail (staff). `mine` on each
 * message is already resolved server-side per the caller's role, so this
 * component doesn't need to know who's viewing it - except for whether to
 * offer a send box: the backend only lets a PARENT or staff member send
 * (MessageService#send returns 403 for STUDENT), so a STUDENT gets a
 * read-only note instead of a form that can only fail.
 */
export function MessageThread({ studentId }: { studentId: string }) {
  const { role } = useAuth()
  const canSend = role !== 'STUDENT'

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
    if (!canSend || !body.trim()) return
    setError(null)
    setSending(true)
    try {
      const sent = await api.post<Message>(`/api/v1/messages/students/${studentId}`, { body })
      setMessages((prev) => [...prev, sent])
      setBody('')
    } catch (err) {
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
          <p className="text-slate text-sm text-center py-6">
            {canSend ? 'No messages yet. Say hello!' : 'No messages yet.'}
          </p>
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

      {canSend ? (
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
      ) : (
        <div className="border-t border-line p-3">
          <p className="text-xs text-slate-light">
            Read only — you can follow this conversation, but only a parent or a teacher can send messages.
          </p>
        </div>
      )}
    </div>
  )
}
