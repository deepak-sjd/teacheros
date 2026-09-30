export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(amount)
}

export function formatDate(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  const isToday = d.toDateString() === new Date().toDateString()
  const time = d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
  return isToday ? time : `${d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}, ${time}`
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

export const MEETING_PLATFORM_LABELS: Record<string, string> = {
  ZOOM: 'Zoom',
  GOOGLE_MEET: 'Google Meet',
  MICROSOFT_TEAMS: 'Microsoft Teams',
  OTHER: 'Video call',
}

/**
 * Whether a weekly timetable slot's class is "live enough to join" right
 * now - true from `joinBufferMinutes` before its start time through its
 * end time, on the matching day of week. Used to switch the student/parent
 * Timetable's button between "Join now" and a quieter "Link available"
 * state, rather than showing an always-identical button whether or not
 * class is actually happening.
 */
export function isSlotLiveNow(
  dayOfWeek: number,
  startTime: string,
  endTime: string,
  joinBufferMinutes = 10,
): boolean {
  const now = new Date()
  const isoDayOfWeek = now.getDay() === 0 ? 7 : now.getDay() // Mon=1..Sun=7, matches backend
  if (isoDayOfWeek !== dayOfWeek) return false

  const [startH, startM] = startTime.slice(0, 5).split(':').map(Number)
  const [endH, endM] = endTime.slice(0, 5).split(':').map(Number)
  const nowMinutes = now.getHours() * 60 + now.getMinutes()
  const startMinutes = startH * 60 + startM - joinBufferMinutes
  const endMinutes = endH * 60 + endM

  return nowMinutes >= startMinutes && nowMinutes <= endMinutes
}

export function initials(name: string): string {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
}
