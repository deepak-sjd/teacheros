import type { Role } from '@/lib/types'

/**
 * Every role with a full portal. Deliberately does NOT include ACCOUNTANT or
 * COUNSELLOR: those roles are permission-scoped (backend role_permissions),
 * and most shared pages (Alerts, Announcements, ...) would render staff UI
 * full of 403s for them. Add them per module via the constants below.
 */
export const ALL_ROLES: readonly Role[] = ['OWNER', 'ADMIN', 'TEACHER', 'STAFF', 'PARENT', 'STUDENT']
/** Full operational staff. */
export const MANAGER_ROLES: readonly Role[] = ['OWNER', 'ADMIN', 'TEACHER']
/** Managers plus the limited STAFF role. */
export const STAFF_ROLES: readonly Role[] = ['OWNER', 'ADMIN', 'TEACHER', 'STAFF']
/** Parent/student portal roles. */
export const FAMILY_ROLES: readonly Role[] = ['PARENT', 'STUDENT']
/** Fee/finance staff - sees only the modules that list it explicitly. */
export const ACCOUNTANT_ROLES: readonly Role[] = ['ACCOUNTANT']
/** Admissions staff - sees only the modules that list it explicitly. */
export const COUNSELLOR_ROLES: readonly Role[] = ['COUNSELLOR']

/** Used when a nav entry has no icon of its own (same glyph as Overview). */
export const DEFAULT_NAV_ICON = 'M3 3h7v9H3z M14 3h7v5h-7z M14 12h7v9h-7z M3 16h7v5H3z'

export interface NavEntry {
  to: string
  label: string
  /** Roles that see this entry. Use separate entries for per-role labels/ordering. */
  roles: readonly Role[]
  /**
   * Sort position within a role's sidebar (ascending). Orders only need to be
   * unique per role, not globally. Leave gaps of 10 so others can slot in.
   */
  order: number
  /** SVG path data for a 24x24 stroke icon (Lucide-style). Falls back to DEFAULT_NAV_ICON. */
  icon?: string
}

/** Identity helper - gives module files type-checking without extra imports. */
export function defineNav(entries: NavEntry[]): NavEntry[] {
  return entries
}
