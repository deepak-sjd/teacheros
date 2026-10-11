import { useEffect, useState } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { EmptyState, ErrorBanner, Skeleton } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { FeedAnnouncement, Page } from '@/lib/types'

// Account-level feed (not per-child): the backend scopes it to every linked
// child's batch/grade plus ALL, so no StudentSectionShell / child switcher.
export function StudentAnnouncements() {
  const [items, setItems] = useState<FeedAnnouncement[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<Page<FeedAnnouncement>>('/api/v1/announcements/feed', { size: 50 })
      .then((res) => setItems(res.content))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load announcements.'))
  }, [])

  async function handleOpen(a: FeedAnnouncement) {
    if (a.read) return
    try {
      await api.patch(`/api/v1/announcements/${a.id}/read`)
      setItems((prev) => (prev ? prev.map((it) => (it.id === a.id ? { ...it, read: true } : it)) : prev))
    } catch {
      // A failed read receipt shouldn't get in the way of reading.
    }
  }

  return (
    <Layout>
      <PageHeader title="Announcements" description="Circulars from your school. Tap one to mark it as read." />

      {error && <ErrorBanner message={error} />}

      {items === null && !error ? (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-20 rounded-lg" />
          ))}
        </div>
      ) : items && items.length === 0 ? (
        <EmptyState title="No announcements" description="Circulars from your school will show up here." />
      ) : (
        <div className="space-y-2">
          {items?.map((a) => (
            <button
              key={a.id}
              onClick={() => handleOpen(a)}
              className={`block w-full text-left rounded-lg border border-line p-4 transition-colors hover:bg-paper-dim/60 ${
                a.read ? 'bg-white' : 'bg-chalk/40'
              }`}
            >
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-start gap-2">
                  {!a.read && <span className="h-2 w-2 rounded-full bg-danger shrink-0 mt-1.5" />}
                  <div>
                    <p className="font-medium text-ink text-sm">{a.title}</p>
                    <p className="text-sm text-slate mt-0.5 whitespace-pre-wrap">{a.body}</p>
                    <p className="text-xs text-slate-light mt-2">From {a.createdByName}</p>
                  </div>
                </div>
                <span className="text-xs text-slate-light whitespace-nowrap">{formatDateTime(a.createdAt)}</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </Layout>
  )
}
