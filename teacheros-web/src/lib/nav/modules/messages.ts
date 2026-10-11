import { FAMILY_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/messages', label: 'Messages', roles: FAMILY_ROLES, order: 110, icon: 'M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M22 6l-10 7L2 6' },
])
