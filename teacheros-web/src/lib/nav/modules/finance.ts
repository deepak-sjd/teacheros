import { defineNav } from '../types'

// Matches the backend: FeePermissions.EDIT = OWNER, ADMIN, ACCOUNTANT.
// Orders 81-84 sit right after Fees (80) and are unique per role.
const FINANCE_ROLES = ['OWNER', 'ADMIN', 'ACCOUNTANT'] as const

export default defineNav([
  { to: '/app/fee-setup', label: 'Fee Setup', roles: FINANCE_ROLES, order: 81, icon: 'M12 1v22 M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6' },
  { to: '/app/fee-assign', label: 'Assign Fees', roles: FINANCE_ROLES, order: 82, icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M19 8v6 M22 11h-6' },
  { to: '/app/fee-discounts', label: 'Discounts & Rules', roles: FINANCE_ROLES, order: 83, icon: 'M19 5L5 19 M6.5 9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M17.5 20a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z' },
  { to: '/app/finance-reports', label: 'Finance Reports', roles: FINANCE_ROLES, order: 84, icon: 'M18 20V10 M12 20V4 M6 20v-6' },
])
