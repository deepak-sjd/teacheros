const STORAGE_KEY = 'teacheros_public_school'

/**
 * Which school's public data this visitor is looking at. Order:
 * ?school=<slug> in the URL (remembered for the tab), then
 * VITE_PUBLIC_TENANT_SLUG. It is a public slug, never a tenant id.
 */
export function getPublicSchoolSlug(): string {
  try {
    const fromQuery = new URLSearchParams(window.location.search).get('school')?.trim().toLowerCase()
    if (fromQuery) {
      sessionStorage.setItem(STORAGE_KEY, fromQuery)
      return fromQuery
    }
    const stored = sessionStorage.getItem(STORAGE_KEY)
    if (stored) return stored
  } catch {
    /* storage unavailable - fall through to env */
  }
  return (import.meta.env.VITE_PUBLIC_TENANT_SLUG ?? '').trim().toLowerCase()
}
