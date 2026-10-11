import type { Role } from './auth'

// --- Settings: my profile + tenant (school) settings ---
// Mirrors backend/.../modules/user/dto/UserDtos.java and
// modules/tenant/dto/TenantDtos.java exactly.

export interface MyProfileResponse {
  id: string
  phoneNumber: string
  displayName: string
  email: string | null
  role: Role
  tenantName: string | null
}

// phoneNumber is deliberately not here - it's the login identity (OTP
// target), not an editable contact field. See UserDtos.UpdateProfileRequest's
// javadoc for why changing it isn't a plain profile edit.
export interface UpdateProfileRequest {
  displayName: string
  email?: string
}

export type TenantType = 'COACHING' | 'SCHOOL'

export interface TenantSettingsResponse {
  id: string
  name: string
  // Short-lived signed URL, same pattern as DownloadUrlResponse - null
  // when no logo has been uploaded yet.
  logoUrl: string | null
  // Public slug used in the shareable course-page link.
  slug: string
  tenantType: TenantType
  enabledModules: string[]
}

// Mirrors TenantDtos.TenantConfigResponse exactly.
export interface TenantConfigResponse {
  tenantType: TenantType
  enabledModules: string[]
  locale: string
  timezone: string
  primaryColor: string | null
  secondaryColor: string | null
}

// PATCH: omit a field to leave it unchanged. For the two colours, '' clears the value.
export interface UpdateTenantConfigRequest {
  tenantType?: TenantType
  enabledModules?: string[]
  locale?: string
  timezone?: string
  primaryColor?: string
  secondaryColor?: string
}

export interface UpdateTenantRequest {
  name: string
}
