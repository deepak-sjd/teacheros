// Mirrors backend/.../modules/staff/dto/StaffDtos.java and
// modules/permission/dto/PermissionAdminDtos.java exactly.

import type { Role } from './auth'

export interface StaffMember {
  id: string
  displayName: string
  phoneNumber: string
  role: Role
  active: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface CreateStaffRequest {
  displayName: string
  phoneNumber: string
  role: Role
}

export interface ChangeStaffRoleRequest {
  role: Role
}

export interface PermissionInfo {
  name: string
  group: string
  description: string
}

// roles: column order (OWNER first). lockedRoles: not editable (OWNER).
// grants: role -> permission names that role currently holds.
export interface PermissionMatrix {
  roles: Role[]
  lockedRoles: Role[]
  permissions: PermissionInfo[]
  grants: Record<string, string[]>
}

export interface SetPermissionRequest {
  role: Role
  permission: string
  granted: boolean
}

export interface PermissionGrant {
  role: Role
  permission: string
  granted: boolean
}
