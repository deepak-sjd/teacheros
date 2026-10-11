import { ALL_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app', label: 'Overview', roles: ALL_ROLES, order: 0, icon: 'M3 3h7v9H3z M14 3h7v5h-7z M14 12h7v9h-7z M3 16h7v5H3z' },
])
