import { ACCOUNTANT_ROLES, STAFF_ROLES, defineNav } from '../types'

export default defineNav([
  {
    to: '/app/reports',
    label: 'Reports',
    roles: [...STAFF_ROLES, ...ACCOUNTANT_ROLES],
    order: 110,
    icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8',
  },
])