import { MANAGER_ROLES, defineNav } from '../types'

export default defineNav([
  { to: '/app/videos', label: 'Video Library', roles: MANAGER_ROLES, order: 66, icon: 'M23 7l-7 5 7 5V7z M1 5h15v14H1z' },
])
