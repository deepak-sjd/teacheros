import { defineNav } from '../types'

// Owner only, just above Settings (order 120).
export default defineNav([
  {
    to: '/app/ai-usage',
    label: 'AI Usage',
    roles: ['OWNER'],
    order: 110,
    // Lucide "sparkles" (simplified)
    icon: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z',
  },
])
