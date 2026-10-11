import { STAFF_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/analytics', label: 'Analytics', roles: STAFF_ROLES, order: 100, icon: 'M18 20V10 M12 20V4 M6 20v-6' },
])
