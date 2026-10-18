import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '@/lib/api'
import type { StudentProfileExtras, UpdateStudentProfileRequest } from '@/lib/types'
import { Button, EmptyState, ErrorBanner, Input, Select, Skeleton, Textarea } from '@/components/ui'

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-']
const PHONE_PATTERN = /^\+?[0-9]{10,15}$/
const EMPTY = '\u2014'

interface FormState {
  previousSchool: string
  bloodGroup: string
  address: string
  guardian2Name: string
  guardian2Relation: string
  guardian2Phone: string
  medicalNotes: string
}

function toForm(p: StudentProfileExtras): FormState {
  return {
    previousSchool: p.previousSchool ?? '',
    bloodGroup: p.bloodGroup ?? '',
    address: p.address ?? '',
    guardian2Name: p.guardian2Name ?? '',
    guardian2Relation: p.guardian2Relation ?? '',
    guardian2Phone: p.guardian2Phone ?? '',
    medicalNotes: p.medicalNotes ?? '',
  }
}

const blankToNull = (v: string) => (v.trim() === '' ? null : v.trim())

function Field({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-light">{label}</dt>
      <dd className="text-sm text-ink mt-0.5 whitespace-pre-wrap">{value ?? EMPTY}</dd>
    </div>
  )
}

/**
 * Profile extras for one student. Staff can edit; a parent sees their own
 * child's profile read-only (the backend 404s anyone else). Medical notes are
 * only rendered when the API says the caller may see them - a STUDENT account
 * or another parent never gets them, so there is nothing to hide client-side.
 */
export function StudentProfileTab({ studentId, canEdit }: { studentId: string; canEdit: boolean }) {
  const [profile, setProfile] = useState<StudentProfileExtras | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<FormState | null>(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setProfile(null)
    setError(null)
    setEditing(false)
    api
      .get<StudentProfileExtras>(`/api/v1/students/${studentId}/profile`)
      .then((p) => {
        if (!cancelled) setProfile(p)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Could not load the profile.')
      })
    return () => {
      cancelled = true
    }
  }, [studentId])

  function startEditing() {
    if (!profile) return
    setForm(toForm(profile))
    setFormError(null)
    setEditing(true)
  }

  function setField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev))
  }

  async function handleSave(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    if (form.guardian2Phone.trim() !== '' && !PHONE_PATTERN.test(form.guardian2Phone.trim())) {
      setFormError('Enter a valid phone number for the second guardian (10 to 15 digits).')
      return
    }
    setFormError(null)
    setSaving(true)
    const body: UpdateStudentProfileRequest = {
      previousSchool: blankToNull(form.previousSchool),
      medicalNotes: blankToNull(form.medicalNotes),
      bloodGroup: blankToNull(form.bloodGroup),
      address: blankToNull(form.address),
      guardian2Name: blankToNull(form.guardian2Name),
      guardian2Phone: blankToNull(form.guardian2Phone),
      guardian2Relation: blankToNull(form.guardian2Relation),
    }
    try {
      const updated = await api.put<StudentProfileExtras>(`/api/v1/students/${studentId}/profile`, body)
      setProfile(updated)
      setEditing(false)
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Could not save the profile.')
    } finally {
      setSaving(false)
    }
  }

  if (error) return <ErrorBanner message={error} />
  if (!profile) return <Skeleton className="h-48 rounded-lg" />

  if (editing && form) {
    return (
      <form onSubmit={handleSave} className="border border-line rounded-lg bg-white p-5 space-y-4 max-w-2xl">
        {formError && <ErrorBanner message={formError} />}
        <Input label="Previous school" value={form.previousSchool} maxLength={255} onChange={(e) => setField('previousSchool', e.target.value)} />
        <Select label="Blood group" value={form.bloodGroup} onChange={(e) => setField('bloodGroup', e.target.value)}>
          <option value="">Not recorded</option>
          {BLOOD_GROUPS.map((g) => (
            <option key={g} value={g}>
              {g}
            </option>
          ))}
        </Select>
        <Textarea label="Address" value={form.address} maxLength={1000} onChange={(e) => setField('address', e.target.value)} />

        <div className="border-t border-line pt-4 space-y-4">
          <p className="text-sm font-medium text-ink">Second guardian</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Name" value={form.guardian2Name} maxLength={255} onChange={(e) => setField('guardian2Name', e.target.value)} />
            <Input label="Relation" value={form.guardian2Relation} maxLength={50} placeholder="e.g. Mother" onChange={(e) => setField('guardian2Relation', e.target.value)} />
          </div>
          <Input label="Phone number" type="tel" value={form.guardian2Phone} onChange={(e) => setField('guardian2Phone', e.target.value)} />
          <p className="text-xs text-slate">
            Contact details only. A second guardian phone does not give that person a parent login.
          </p>
        </div>

        <div className="border-t border-line pt-4">
          <Textarea
            label="Medical notes"
            value={form.medicalNotes}
            maxLength={2000}
            onChange={(e) => setField('medicalNotes', e.target.value)}
          />
          <p className="text-xs text-slate mt-1.5">Sensitive. Visible to staff and this student&apos;s guardian only.</p>
        </div>

        <div className="flex gap-2">
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save profile'}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setEditing(false)} disabled={saving}>
            Cancel
          </Button>
        </div>
      </form>
    )
  }

  const noDetails =
    !profile.previousSchool &&
    !profile.bloodGroup &&
    !profile.address &&
    !profile.guardian2Name &&
    !profile.guardian2Phone &&
    !(profile.medicalNotesVisible && profile.medicalNotes)

  return (
    <div className="max-w-2xl">
      {canEdit && (
        <div className="mb-4">
          <Button variant="secondary" onClick={startEditing}>
            Edit profile
          </Button>
        </div>
      )}

      {noDetails ? (
        <EmptyState
          title="No profile details yet"
          description={canEdit ? 'Use "Edit profile" to add previous school, address and more.' : 'The school has not added any profile details yet.'}
        />
      ) : (
        <div className="border border-line rounded-lg bg-white p-5 space-y-5">
          <dl className="grid gap-5 sm:grid-cols-2">
            <Field label="Previous school" value={profile.previousSchool} />
            <Field label="Blood group" value={profile.bloodGroup} />
          </dl>
          <dl>
            <Field label="Address" value={profile.address} />
          </dl>
          <div className="border-t border-line pt-5">
            <p className="text-sm font-medium text-ink mb-3">Second guardian</p>
            <dl className="grid gap-5 sm:grid-cols-3">
              <Field label="Name" value={profile.guardian2Name} />
              <Field label="Relation" value={profile.guardian2Relation} />
              <Field label="Phone" value={profile.guardian2Phone} />
            </dl>
          </div>
          {profile.medicalNotesVisible && (
            <div className="border-t border-line pt-5">
              <p className="text-sm font-medium text-ink mb-1">Medical notes</p>
              <p className="text-xs text-slate mb-3">Visible to staff and this student&apos;s guardian only.</p>
              <p className="text-sm text-ink whitespace-pre-wrap">{profile.medicalNotes ?? EMPTY}</p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
