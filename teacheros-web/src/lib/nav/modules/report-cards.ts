import { MANAGER_ROLES, defineNav } from '../types'

// Slots 72 and 74 sit between Tests (70) and Fees (80) for the manager roles.
export default defineNav([
  {
    to: '/app/exam-terms',
    label: 'Terms & Grades',
    roles: MANAGER_ROLES,
    order: 72,
    icon: 'M8 2v4 M16 2v4 M3 4h18v18H3z M3 10h18',
  },
  {
    to: '/app/report-remarks',
    label: 'Report Remarks',
    roles: MANAGER_ROLES,
    order: 74,
    icon: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  },
])
