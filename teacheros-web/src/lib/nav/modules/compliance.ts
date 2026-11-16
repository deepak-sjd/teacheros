import { defineNav } from '../types'

// Data-erasure requests (privacy compliance). OWNER and ADMIN can see the list;
// only OWNER can approve or reject (the page and the backend both enforce that).
export default defineNav([
  {
    to: '/app/erasure-requests',
    label: 'Erasure Requests',
    roles: ['OWNER', 'ADMIN'],
    order: 115,
    // Lucide "shield-check" (simplified)
    icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z M9 12l2 2 4-4',
  },
])
