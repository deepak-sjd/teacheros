import { ALL_ROLES, defineNav } from '../types'

// One entry for every role: staff land on the create/manage page, PARENT and
// STUDENT land on the feed. AnnouncementsRoute picks the right page by role.
export default defineNav([
  {
    to: '/app/announcements',
    label: 'Announcements',
    roles: ALL_ROLES,
    order: 85,
    // Lucide "megaphone"
    icon: 'M3 11l18-5v12L3 14v-3z M11.6 16.8a3 3 0 1 1-5.8-1.6',
  },
])
