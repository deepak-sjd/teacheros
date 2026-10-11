import { defineNav } from '../types'

// order: set this to (study-material order + 1) so it sits right after Study Material.
export default defineNav([{ to: '/app/learn', label: 'My Courses', roles: ['STUDENT'], order: 85 }])
