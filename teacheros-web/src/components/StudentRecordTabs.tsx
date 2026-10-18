import { useState, type ReactNode } from 'react'
import { StudentProfileTab } from '@/components/StudentProfileTab'
import { StudentDocumentsTab } from '@/components/StudentDocumentsTab'

type TabKey = 'overview' | 'profile' | 'documents'

/**
 * Tab bar for one student's record. Staff pass `overview` (the existing
 * StudentProfile page) and get Overview / Profile / Documents; the parent and
 * student page passes no overview and gets Profile / Documents only.
 * A tab's content mounts the first time it is opened and then stays mounted,
 * so switching back doesn't refetch (or reset an unsent message).
 */
export function StudentRecordTabs({
  studentId,
  canEdit,
  overview,
}: {
  studentId: string
  /** Staff only: edit the profile, upload and delete documents. */
  canEdit: boolean
  overview?: ReactNode
}) {
  const tabs: { key: TabKey; label: string }[] = [
    ...(overview ? [{ key: 'overview' as const, label: 'Overview' }] : []),
    { key: 'profile', label: 'Profile' },
    { key: 'documents', label: 'Documents' },
  ]
  const [active, setActive] = useState<TabKey>(tabs[0].key)
  const [opened, setOpened] = useState<Set<TabKey>>(() => new Set([tabs[0].key]))

  function select(key: TabKey) {
    setActive(key)
    setOpened((prev) => (prev.has(key) ? prev : new Set(prev).add(key)))
  }

  const panel = (key: TabKey, content: ReactNode) =>
    opened.has(key) ? (
      <div role="tabpanel" hidden={active !== key}>
        {content}
      </div>
    ) : null

  return (
    <div>
      <div role="tablist" className="flex gap-1 border-b border-line mb-6">
        {tabs.map((t) => (
          <button
            key={t.key}
            role="tab"
            aria-selected={active === t.key}
            onClick={() => select(t.key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              active === t.key ? 'border-ink text-ink' : 'border-transparent text-slate hover:text-ink'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {overview && panel('overview', overview)}
      {panel('profile', <StudentProfileTab studentId={studentId} canEdit={canEdit} />)}
      {panel('documents', <StudentDocumentsTab studentId={studentId} canManage={canEdit} />)}
    </div>
  )
}
