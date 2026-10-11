export type Role = 'OWNER' | 'TEACHER' | 'STAFF' | 'STUDENT' | 'PARENT' | 'ADMIN' | 'ACCOUNTANT' | 'COUNSELLOR'

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface JwtClaims {
  sub: string // userId
  tenantId: string
  role: Role
  iat: number
  exp: number
}
