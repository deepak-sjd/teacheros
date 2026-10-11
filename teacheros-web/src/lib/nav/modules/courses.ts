import { MANAGER_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/courses', label: 'Courses', roles: MANAGER_ROLES, order: 65, icon: 'M22 10v6 M2 10l10-5 10 5-10 5z M6 12v5c3 3 9 3 12 0v-5' },
])
