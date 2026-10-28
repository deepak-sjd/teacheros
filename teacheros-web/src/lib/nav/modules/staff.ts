import { defineNav } from '../types'

// OWNER and ADMIN manage staff; the permissions page is read-only for ADMIN
// (the backend only lets OWNER change it). Not driven by the permission matrix
// on purpose - managing people/permissions must not be grantable by it.
export default defineNav([
  { to: '/app/staff', label: 'Staff', roles: ['OWNER', 'ADMIN'], order: 121, icon: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75' },
  { to: '/app/permissions', label: 'Permissions', roles: ['OWNER', 'ADMIN'], order: 122, icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z' },
])
