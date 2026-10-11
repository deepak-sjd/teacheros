import type { ReactNode } from 'react'
import { api } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { TimetableSlot } from '@/lib/types'

/**
 * The "Join" link for a timetable slot. Still a plain <a target="_blank">
 * so the browser opens the meeting exactly as before (no popup-blocker
 * risk); the click additionally fires a log request in the background.
 * The log is best-effort: a failed or rejected request must never stop a
 * student reaching their class, so errors are swallowed.
 *
 * Only a STUDENT's click is logged - the endpoint is student-only, and a
 * parent opening the link isn't the student joining.
 */
export function JoinClassLink({
  slot,
  className,
  children,
}: {
  slot: TimetableSlot
  className?: string
  children: ReactNode
}) {
  const { role } = useAuth()

  function handleClick() {
    if (role !== 'STUDENT') return
    api.post(`/api/v1/timetable/slots/${slot.id}/join`).catch(() => {})
  }

  return (
    <a
      href={slot.meetingLink ?? undefined}
      target="_blank"
      rel="noopener noreferrer"
      onClick={handleClick}
      className={className}
    >
      {children}
    </a>
  )
}
