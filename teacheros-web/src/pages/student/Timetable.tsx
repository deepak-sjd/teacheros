import { useEffect, useState } from 'react'
import { StudentSectionShell } from '@/components/StudentSectionShell'
import { EmptyState } from '@/components/ui'
import { api } from '@/lib/api'
import { isSlotLiveNow, MEETING_PLATFORM_LABELS } from '@/lib/format'
import type { Student, TimetableSlot } from '@/lib/types'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function JoinButton({ slot }: { slot: TimetableSlot }) {
  if (!slot.meetingLink) return null
  const live = isSlotLiveNow(slot.dayOfWeek, slot.startTime, slot.endTime)
  return (
    <a
      href={slot.meetingLink}
      target="_blank"
      rel="noopener noreferrer"
      className={`mt-1.5 inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-medium transition-colors ${
        live ? 'bg-success text-white hover:opacity-90' : 'bg-paper-dim text-slate hover:text-ink'
      }`}
    >
      {live ? '● Join now' : `${MEETING_PLATFORM_LABELS[slot.meetingPlatform ?? 'OTHER']} link`}
    </a>
  )
}

function TimetableGrid({ student }: { student: Student }) {
  const [slots, setSlots] = useState<TimetableSlot[] | null>(null)

  useEffect(() => {
    if (!student.batchId) {
      setSlots([])
      return
    }
    setSlots(null)
    api.get<TimetableSlot[]>(`/api/v1/timetable/batches/${student.batchId}`).then(setSlots)
  }, [student.batchId])

  if (slots === null) return <p className="text-slate text-sm">Loading…</p>
  if (!student.batchId || slots.length === 0) {
    return <EmptyState title="No schedule set" description="Your teacher hasn't set a weekly schedule for this batch yet." />
  }

  return (
    <div className="overflow-x-auto">
      <div className="grid grid-cols-7 gap-3 min-w-[640px]">
        {DAYS.map((day, i) => {
          const dayOfWeek = i + 1
          const daySlots = slots
            .filter((s) => s.dayOfWeek === dayOfWeek)
            .sort((a, b) => a.startTime.localeCompare(b.startTime))
          return (
            <div key={day}>
              <div className="text-sm font-medium text-slate mb-2">{day}</div>
              <div className="space-y-2">
                {daySlots.length === 0 ? (
                  <div className="text-xs text-slate-light">—</div>
                ) : (
                  daySlots.map((slot) => (
                    <div key={slot.id} className="rounded-md border border-line bg-white p-2 text-xs">
                      <div className="font-medium text-ink">
                        {slot.startTime.slice(0, 5)}–{slot.endTime.slice(0, 5)}
                      </div>
                      {slot.subject && <div className="text-slate">{slot.subject}</div>}
                      <JoinButton slot={slot} />
                    </div>
                  ))
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export function StudentTimetable() {
  return (
    <StudentSectionShell title="Timetable" description="Weekly class schedule.">
      {(student) => <TimetableGrid student={student} />}
    </StudentSectionShell>
  )
}
