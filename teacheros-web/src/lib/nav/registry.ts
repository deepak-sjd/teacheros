import type { Role } from '@/lib/types'
import type { NavEntry } from './types'

// Every file in ./modules is picked up automatically - a module registers its
// nav items by adding ONE file there (default export = defineNav([...])).
// Nothing in this file or in Layout.tsx needs editing.
const loaded = import.meta.glob<NavEntry[]>('./modules/*.ts', { eager: true, import: 'default' })

const ALL_ENTRIES: NavEntry[] = Object.values(loaded).flat()

if (import.meta.env.DEV) {
  const seen = new Set<string>()
  for (const entry of ALL_ENTRIES) {
    for (const role of entry.roles) {
      const key = `${role}:${entry.to}`
      if (seen.has(key)) console.warn(`[nav] duplicate nav entry for ${role} -> ${entry.to}`)
      seen.add(key)
    }
  }
}

export function navItemsForRole(role: Role | null): NavEntry[] {
  if (!role) return []
  return ALL_ENTRIES.filter((e) => e.roles.includes(role)).sort(
    (a, b) => a.order - b.order || a.label.localeCompare(b.label),
  )
}
