import { useEffect, useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { Layout, PageHeader } from '@/components/Layout'
import { Badge, Button, Cell, EmptyState, ErrorBanner, Input, Ledger, LedgerSkeleton, Modal, Row, Select } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import { formatDate } from '@/lib/format'
import type { CreateStaffRequest, Role, StaffMember } from '@/lib/types'

const ALL_STAFF_ROLES: Role[] = ['OWNER', 'ADMIN', 'TEACHER', 'STAFF', 'ACCOUNTANT', 'COUNSELLOR']

function roleLabel(role: string): string {
  return role.charAt(0) + role.slice(1).toLowerCase()
}

export function StaffPage() {
  const { role, userId } = useAuth()
  const isOwner = role === 'OWNER'
  const canManage = role === 'OWNER' || role === 'ADMIN'
  // An ADMIN can manage everyone except owners (the backend enforces this too).
  const assignableRoles = isOwner ? ALL_STAFF_ROLES : ALL_STAFF_ROLES.filter((r) => r !== 'OWNER')

  const [staff, setStaff] = useState<StaffMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [addOpen, setAddOpen] = useState(false)

  function load() {
    setLoading(true)
    setError(null)
    api
      .get<StaffMember[]>('/api/v1/staff')
      .then(setStaff)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load staff.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (canManage) load()
  }, [canManage])

  if (!canManage) return <Navigate to="/app" replace />

  async function run(member: StaffMember, action: () => Promise<StaffMember>) {
    setBusyId(member.id)
    setError(null)
    try {
      const updated = await action()
      setStaff((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That change could not be saved.')
    } finally {
      setBusyId(null)
    }
  }

  function changeRole(member: StaffMember, next: Role) {
    if (next === member.role) return
    if (!confirm(`Change ${member.displayName} from ${roleLabel(member.role)} to ${roleLabel(next)}? It applies the next time they sign in or their session refreshes.`)) return
    run(member, () => api.patch<StaffMember>(`/api/v1/staff/${member.id}/role`, { role: next }))
  }

  function deactivate(member: StaffMember) {
    if (!confirm(`Deactivate ${member.displayName}? They are signed out of every device and can't log in until reactivated.`)) return
    run(member, () => api.post<StaffMember>(`/api/v1/staff/${member.id}/deactivate`))
  }

  function reactivate(member: StaffMember) {
    run(member, () => api.post<StaffMember>(`/api/v1/staff/${member.id}/reactivate`))
  }

  return (
    <Layout>
      <PageHeader
        title="Staff"
        description="Add the people who help run your school and choose what role each one has."
        action={<Button onClick={() => setAddOpen(true)}>Add staff member</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <LedgerSkeleton columns={6} />
      ) : staff.length === 0 ? (
        <EmptyState title="No staff yet" description="Add a staff member. They sign in with their phone number and a one-time code." />
      ) : (
        <Ledger columns={['Name', 'Phone', 'Role', 'Status', 'Last sign-in', '']}>
          {staff.map((m) => {
            const isSelf = m.id === userId
            const ownerLocked = m.role === 'OWNER' && !isOwner
            const busy = busyId === m.id
            return (
              <Row key={m.id}>
                <Cell className="font-medium">
                  {m.displayName}
                  {isSelf && <span className="text-xs text-slate-light"> (you)</span>}
                </Cell>
                <Cell>{m.phoneNumber}</Cell>
                <Cell>
                  <Select
                    aria-label={`Role for ${m.displayName}`}
                    value={m.role}
                    disabled={busy || isSelf || ownerLocked}
                    onChange={(e) => changeRole(m, e.target.value as Role)}
                  >
                    {/* keep the current role visible even if this viewer couldn't assign it */}
                    {Array.from(new Set([m.role, ...assignableRoles])).map((r) => (
                      <option key={r} value={r}>
                        {roleLabel(r)}
                      </option>
                    ))}
                  </Select>
                </Cell>
                <Cell>{m.active ? <Badge tone="success">Active</Badge> : <Badge tone="danger">Deactivated</Badge>}</Cell>
                <Cell>{m.lastLoginAt ? formatDate(m.lastLoginAt) : 'Never'}</Cell>
                <Cell>
                  {m.active ? (
                    <Button variant="secondary" disabled={busy || isSelf || ownerLocked} onClick={() => deactivate(m)}>
                      Deactivate
                    </Button>
                  ) : (
                    <Button variant="secondary" disabled={busy || ownerLocked} onClick={() => reactivate(m)}>
                      Reactivate
                    </Button>
                  )}
                </Cell>
              </Row>
            )
          })}
        </Ledger>
      )}

      <AddStaffModal
        open={addOpen}
        roles={assignableRoles}
        onClose={() => setAddOpen(false)}
        onCreated={(created) => {
          setAddOpen(false)
          setStaff((prev) => [...prev, created].sort((a, b) => a.displayName.localeCompare(b.displayName)))
        }}
      />
    </Layout>
  )
}

function AddStaffModal({
  open,
  roles,
  onClose,
  onCreated,
}: {
  open: boolean
  roles: Role[]
  onClose: () => void
  onCreated: (created: StaffMember) => void
}) {
  const [displayName, setDisplayName] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [role, setRole] = useState<Role>('ACCOUNTANT')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setDisplayName('')
      setPhoneNumber('')
      setRole(roles.includes('ACCOUNTANT') ? 'ACCOUNTANT' : roles[0])
      setError(null)
    }
  }, [open, roles])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaving(true)
    try {
      const body: CreateStaffRequest = { displayName: displayName.trim(), phoneNumber: phoneNumber.trim(), role }
      onCreated(await api.post<StaffMember>('/api/v1/staff', body))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add this staff member.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Add staff member">
      {error && <ErrorBanner message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Full name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
        <Input
          label="Phone number"
          type="tel"
          placeholder="919876543210"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
          required
        />
        <Select label="Role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {roles.map((r) => (
            <option key={r} value={r}>
              {roleLabel(r)}
            </option>
          ))}
        </Select>
        <p className="text-xs text-slate">
          They sign in at the normal login page with this phone number and a one-time code, and land in your school with
          this role.
        </p>
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Adding…' : 'Add staff member'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
