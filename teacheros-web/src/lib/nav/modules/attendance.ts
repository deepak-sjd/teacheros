import { MANAGER_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/attendance', label: 'Attendance', roles: MANAGER_ROLES, order: 30, icon: 'M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4 12 14.01l-3-3' },
  {
    to: '/app/checkin',
    label: 'Check In',
    roles: ['STUDENT'],
    order: 30,
    icon: 'M3 7V5a2 2 0 0 1 2-2h2 M17 3h2a2 2 0 0 1 2 2v2 M21 17v2a2 2 0 0 1-2 2h-2 M7 21H5a2 2 0 0 1-2-2v-2 M7 12h10',
  },
])
