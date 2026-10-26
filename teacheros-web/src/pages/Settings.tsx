import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Layout, PageHeader } from '@/components/Layout'
import { Button, ErrorBanner, Input } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { api, ApiError } from '@/lib/api'
import type { MyProfileResponse, TenantSettingsResponse } from '@/lib/types'
import { PrivacySection } from '@/components/PrivacySection'
// inside <div className="space-y-10 max-w-xl"> after the SchoolSection line:

export function Settings() {
  const { role } = useAuth()
  const canEditSchool = role === 'OWNER' || role === 'ADMIN'

  return (
    <Layout>
      <PageHeader title="Settings" description="Your profile, and school settings if you manage this workspace." />
      <div className="space-y-10 max-w-xl">
        {role === 'PARENT' && <PrivacySection />}

        <ProfileSection />
        {canEditSchool && <SchoolSection />}
      </div>
    </Layout>
  )
}

function ProfileSection() {
  const [profile, setProfile] = useState<MyProfileResponse | null>(null)
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    api
      .get<MyProfileResponse>('/api/v1/users/me')
      .then((res) => {
        setProfile(res)
        setDisplayName(res.displayName)
        setEmail(res.email ?? '')
      })
      .finally(() => setLoading(false))
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaved(false)
    setSaving(true)
    try {
      const res = await api.patch<MyProfileResponse>('/api/v1/users/me', {
        displayName,
        email: email || undefined,
      })
      setProfile(res)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your profile.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="text-slate text-sm">Loading…</p>

  return (
    <section>
      <h2 className="font-display text-xl text-ink mb-1">My profile</h2>
      <p className="text-sm text-slate mb-4">{profile?.tenantName ?? '\u2014'} \u00b7 {profile?.role}</p>

      {error && <ErrorBanner message={error} />}
      {saved && (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
          Profile updated.
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="Display name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />

        {/* Phone is the login identity (OTP target) - shown but not
            editable here. Changing it needs its own verify-the-new-number
            flow, which doesn't exist yet, rather than a plain text field
            that could lock the account out or hand it to a typo'd number. */}
        <div>
          <span className="block text-sm font-medium text-ink mb-1.5">Phone number</span>
          <div className="w-full rounded-md border border-line bg-paper-dim px-3 py-2 text-sm text-slate">
            {profile?.phoneNumber}
          </div>
          <p className="text-xs text-slate-light mt-1">
            This is how you log in and can't be changed here. Contact support if it needs to change.
          </p>
        </div>

        <Input
          label="Email (optional)"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
        />

        <div className="flex justify-end pt-2">
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving\u2026' : 'Save changes'}
          </Button>
        </div>
      </form>
    </section>
  )
}

function SchoolSection() {
  const [tenant, setTenant] = useState<TenantSettingsResponse | null>(null)
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [copied, setCopied] = useState(false)

  function load() {
    setLoading(true)
    api
      .get<TenantSettingsResponse>('/api/v1/tenant')
      .then((res) => {
        setTenant(res)
        setName(res.name)
      })
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSaved(false)
    setSaving(true)
    try {
      const res = await api.patch<TenantSettingsResponse>('/api/v1/tenant', { name })
      setTenant(res)
      setSaved(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save school settings.')
    } finally {
      setSaving(false)
    }
  }

  async function handleLogoChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setError(null)
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await api.uploadFile<TenantSettingsResponse>('/api/v1/tenant/logo', formData)
      setTenant(res)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not upload the logo.')
    } finally {
      setUploading(false)
    }
  }

  async function handleRemoveLogo() {
    setError(null)
    try {
      const res = await api.del<TenantSettingsResponse>('/api/v1/tenant/logo')
      setTenant(res)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove the logo.')
    }
  }

  if (loading) return <p className="text-slate text-sm">Loading…</p>

  const publicUrl = tenant?.slug ? `${window.location.origin}/courses?school=${tenant.slug}` : null

  async function copyPublicUrl() {
    if (!publicUrl) return
    try {
      await navigator.clipboard.writeText(publicUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      /* clipboard unavailable - the link stays visible to copy by hand */
    }
  }

  return (
    <section className="pt-10 border-t border-line">
      <h2 className="font-display text-xl text-ink mb-1">School settings</h2>
      <p className="text-sm text-slate mb-4">Visible to every account in your workspace.</p>

      {error && <ErrorBanner message={error} />}
      {saved && (
        <div className="rounded-md bg-success-soft text-success text-sm px-4 py-3 mb-4 border border-success/20">
          School settings updated.
        </div>
      )}

      <div className="mb-6">
        <span className="block text-sm font-medium text-ink mb-1.5">Logo</span>
        <div className="flex items-center gap-4">
          <div className="h-16 w-16 rounded-lg border border-line bg-paper-dim flex items-center justify-center overflow-hidden shrink-0">
            {tenant?.logoUrl ? (
              <img src={tenant.logoUrl} alt="School logo" className="h-full w-full object-cover" />
            ) : (
              <span className="text-xs text-slate-light">None</span>
            )}
          </div>
          <div className="flex gap-2">
            <label>
              <span className="inline-block rounded-md px-4 py-2 text-sm font-medium border border-line bg-white text-ink hover:bg-paper-dim cursor-pointer transition-colors">
                {uploading ? 'Uploading\u2026' : tenant?.logoUrl ? 'Replace logo' : 'Upload logo'}
              </span>
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                disabled={uploading}
                onChange={handleLogoChange}
              />
            </label>
            {tenant?.logoUrl && (
              <Button variant="secondary" onClick={handleRemoveLogo}>
                Remove
              </Button>
            )}
          </div>
        </div>
        <p className="text-xs text-slate-light mt-2">PNG, JPEG, or WebP.</p>
      </div>

      {publicUrl && (
        <div className="mb-6">
          <span className="block text-sm font-medium text-ink mb-1.5">Public course page</span>
          <div className="flex items-center gap-2">
            <code className="flex-1 min-w-0 truncate rounded-lg border border-line bg-paper-dim px-3 py-2 text-xs text-slate">
              {publicUrl}
            </code>
            <Button type="button" variant="secondary" onClick={copyPublicUrl}>
              {copied ? 'Copied' : 'Copy link'}
            </Button>
          </div>
          <p className="text-xs text-slate-light mt-1.5">Share this link so visitors can browse your published courses.</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <Input label="School / coaching center name" value={name} onChange={(e) => setName(e.target.value)} required />
        <div className="flex justify-end pt-2">
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving\u2026' : 'Save changes'}
          </Button>
        </div>
      </form>
    </section>
  )
}
