import { FAMILY_ROLES, MANAGER_ROLES, defineNav } from '../types'

const ICON = 'M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z'

export default defineNav([
  { to: '/app/homework', label: 'Homework', roles: MANAGER_ROLES, order: 40, icon: ICON },
  { to: '/app/homework', label: 'Homework', roles: FAMILY_ROLES, order: 45, icon: ICON },
])
