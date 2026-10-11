import { MANAGER_ROLES, defineNav } from '../types'

export default defineNav([{ to: '/app/batches', label: 'Batches', roles: MANAGER_ROLES, order: 10 }])
