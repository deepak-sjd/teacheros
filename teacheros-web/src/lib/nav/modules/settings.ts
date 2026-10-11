import { ACCOUNTANT_ROLES, ALL_ROLES, COUNSELLOR_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/settings', label: 'Settings', roles: [...ALL_ROLES, ...ACCOUNTANT_ROLES, ...COUNSELLOR_ROLES], order: 120, icon: 'M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6' },
])
