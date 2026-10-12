import { MANAGER_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/batches', label: 'Batches', roles: MANAGER_ROLES, order: 10, icon: 'M12 2 2 7l10 5 10-5-10-5z M2 17l10 5 10-5 M2 12l10 5 10-5' },
])