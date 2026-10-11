import { ACCOUNTANT_ROLES, ALL_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/fees', label: 'Fees', roles: [...ALL_ROLES, ...ACCOUNTANT_ROLES], order: 80, icon: 'M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z M1 10h22' },
])
