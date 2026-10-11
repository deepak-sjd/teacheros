import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { Button, Input } from '@/components/ui'
import type { Page, Student } from '@/lib/types'

/**
 * Searchable student picker - queries GET /students?search= as you type
 * (debounced), so it finds students beyond any fixed first page.
 */
export function StudentPicker({
  label = 'Student',
  value,
  onChange,
}: {
  label?: string
  value: Student | null
  onChange: (student: Student | null) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Student[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    let cancelled = false
    const handle = setTimeout(() => {
      setLoading(true)
      api
        .get<Page<Student>>('/api/v1/students', { search: query.trim() || undefined, size: 10 })
        .then((res) => {
          if (!cancelled) setResults(res.content)
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    }, 250)
    return () => {
      cancelled = true
      clearTimeout(handle)
    }
  }, [query, open])

  if (value) {
    return (
      <div>
        <span className="block text-sm font-medium text-ink mb-1.5">{label}</span>
        <div className="flex items-center justify-between rounded-md border border-line bg-white px-3 py-2 text-sm">
          <span className="text-ink">
            {value.fullName}
            {value.grade ? <span className="text-slate"> {'\u00b7'} Grade {value.grade}</span> : null}
          </span>
          <Button type="button" variant="ghost" onClick={() => onChange(null)}>
            Change
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="relative">
      <Input
        label={label}
        placeholder="Search by name or guardian phone"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        autoComplete="off"
      />
      {open && (
        <div className="absolute z-10 mt-1 w-full rounded-md border border-line bg-white shadow-lg max-h-56 overflow-y-auto">
          {loading && results.length === 0 ? (
            <p className="px-3 py-2 text-sm text-slate">Searching…</p>
          ) : results.length === 0 ? (
            <p className="px-3 py-2 text-sm text-slate">No students found.</p>
          ) : (
            results.map((s) => (
              <button
                key={s.id}
                type="button"
                // mouseDown, not click - fires before the input's blur closes the list.
                onMouseDown={(e) => {
                  e.preventDefault()
                  onChange(s)
                  setQuery('')
                  setOpen(false)
                }}
                className="block w-full text-left px-3 py-2 text-sm text-ink hover:bg-paper-dim"
              >
                {s.fullName}
                <span className="text-slate">
                  {s.grade ? ` \u00b7 Grade ${s.grade}` : ''} {'\u00b7'} {s.guardianPhone}
                </span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
