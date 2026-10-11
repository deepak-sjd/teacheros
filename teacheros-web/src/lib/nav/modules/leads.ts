import { COUNSELLOR_ROLES, STAFF_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/leads', label: 'Leads', roles: [...STAFF_ROLES, ...COUNSELLOR_ROLES], order: 25, icon: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z' },
])
