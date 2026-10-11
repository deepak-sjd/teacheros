import { FAMILY_ROLES, MANAGER_ROLES, defineNav } from '../types'

const ICON = 'M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z'

export default defineNav([
  { to: '/app/timetable', label: 'Timetable', roles: MANAGER_ROLES, order: 50, icon: ICON },
  { to: '/app/timetable', label: 'Timetable', roles: FAMILY_ROLES, order: 40, icon: ICON },
])
