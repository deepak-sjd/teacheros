import { FAMILY_ROLES, MANAGER_ROLES, defineNav } from '../types'

export default defineNav([
  {
    to: '/app/study-material',
    label: 'Study Material',
    roles: [...MANAGER_ROLES, ...FAMILY_ROLES],
    order: 60,
    icon: 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z',
  },
])
