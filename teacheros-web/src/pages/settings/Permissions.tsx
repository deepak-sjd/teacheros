import { Fragment, useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Layout, PageHeader } from '@/components/Layout'
import { ErrorBanner, LedgerSkeleton } from '@/components/ui'
import { api, ApiError } from '@/lib/api'
import { useAuth } from '@/lib/auth'
import type { PermissionGrant, PermissionMatrix } from '@/lib/types'

function roleLabel(role: string): string {
  return role.charAt(0) + role.slice(1).toLowerCase()
}

export function PermissionsPage() {
  const { role } = useAuth()
  const canView = role === 'OWNER' || role === 'ADMIN'
  const canEdit = role === 'OWNER' // the backend only lets OWNER change the matrix

  const [matrix, setMatrix] = useState<PermissionMatrix | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<string | null>(null) // "ROLE:PERMISSION" in flight

  useEffect(() => {
    if (!canView) return
    api
      .get<PermissionMatrix>('/api/v1/permissions/matrix')
      .then(setMatrix)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load permissions.'))
  }, [canView])

  const groups = useMemo(() => {
    const out: { group: string; items: PermissionMatrix['permissions'] }[] = []
    for (const p of matrix?.permissions ?? []) {
      const last = out[out.length - 1]
      if (last && last.group === p.group) last.items.push(p)
      else out.push({ group: p.group, items: [p] })
    }
    return out
  }, [matrix])

  if (!canView) return <Navigate to="/app" replace />

  async function toggle(targetRole: string, permission: string, granted: boolean) {
    const key = `${targetRole}:${permission}`
    setPending(key)
    setError(null)
    try {
      const res = await api.put<PermissionGrant>('/api/v1/permissions/grants', {
        role: targetRole,
        permission,
        granted,
      })
      setMatrix((prev) => {
        if (!prev) return prev
        const held = new Set(prev.grants[res.role] ?? [])
        if (res.granted) held.add(res.permission)
        else held.delete(res.permission)
        return { ...prev, grants: { ...prev.grants, [res.role]: [...held] } }
      })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That change could not be saved.')
    } finally {
      setPending(null)
    }
  }

  return (
    <Layout>
      <PageHeader
        title="Permissions"
        description={
          canEdit
            ? 'Choose what each role can do. Changes apply on the very next request and are recorded in the audit log.'
            : 'What each role can do. Only the owner can change this.'
        }
      />

      {error && <ErrorBanner message={error} />}

      {!matrix ? (
        error ? null : <LedgerSkeleton columns={6} />
      ) : (
        <div className="border border-line rounded-lg overflow-x-auto bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line bg-paper-dim">
                <th className="text-left font-medium text-slate px-4 py-3">Permission</th>
                {matrix.roles.map((r) => (
                  <th key={r} className="text-center font-medium text-slate px-3 py-3">
                    {roleLabel(r)}
                    {matrix.lockedRoles.includes(r) && <div className="text-[10px] font-normal text-slate-light">always all</div>}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {groups.map((g) => (
                <Fragment key={g.group}>
                  <tr className="bg-paper-dim/60">
                    <td colSpan={matrix.roles.length + 1} className="px-4 py-2 text-xs font-medium uppercase tracking-wide text-slate">
                      {g.group}
                    </td>
                  </tr>
                  {g.items.map((p) => (
                    <tr key={p.name} className="border-b border-line last:border-0">
                      <td className="px-4 py-3">
                        <div className="text-ink">{p.description}</div>
                        <div className="text-[11px] text-slate-light">{p.name}</div>
                      </td>
                      {matrix.roles.map((r) => {
                        const locked = matrix.lockedRoles.includes(r)
                        const checked = (matrix.grants[r] ?? []).includes(p.name)
                        const key = `${r}:${p.name}`
                        return (
                          <td key={r} className="text-center px-3 py-3">
                            <input
                              type="checkbox"
                              aria-label={`${roleLabel(r)}: ${p.description}`}
                              checked={checked}
                              disabled={locked || !canEdit || pending === key}
                              onChange={(e) => toggle(r, p.name, e.target.checked)}
                            />
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="text-xs text-slate-light mt-4">
        Fee structure changes (heads, structures, discounts, rules) need both "Create fees and record payments" and
        "Manage fee heads, structures…". A person's sidebar menu follows their role; the permissions above decide what the
        server allows them to do.
      </p>
    </Layout>
  )
}
