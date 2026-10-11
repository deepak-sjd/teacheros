import { FAMILY_ROLES, MANAGER_ROLES, defineNav } from '../types'

const ICON =
  'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2 M9 2h6v4H9z M9 12h6 M9 16h4'

export default defineNav([
  { to: '/app/tests', label: 'Tests', roles: MANAGER_ROLES, order: 70, icon: ICON },
  { to: '/app/tests', label: 'Tests & Marks', roles: FAMILY_ROLES, order: 70, icon: ICON },
  { to: '/app/take-test', label: 'Take Test', roles: ['STUDENT'], order: 75, icon: 'M12 20h9 M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4z' },
])
