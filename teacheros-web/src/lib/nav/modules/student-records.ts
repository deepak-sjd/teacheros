import { FAMILY_ROLES, STAFF_ROLES, defineNav } from '../types'

export default defineNav([
  {
    to: '/app/records',
    label: 'Profile & Documents',
    roles: FAMILY_ROLES,
    order: 15,
    // Lucide "user"
    icon: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  },
  {
    // Family: request leave and see decisions. Staff: pending list to approve or reject.
    // LeaveRoute picks the page by role.
    to: '/app/leave',
    label: 'Leave',
    roles: FAMILY_ROLES,
    order: 35,
    // Lucide "calendar"
    icon: 'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  },
  {
    to: '/app/leave',
    label: 'Leave Requests',
    roles: STAFF_ROLES,
    order: 35,
    icon: 'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  },
])
