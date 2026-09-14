import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, setTokens, clearTokens } from './api'
import type { JwtClaims, Role, TokenResponse } from './types'

function decodeJwt(token: string): JwtClaims | null {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(json)
  } catch {
    return null
  }
}

interface AuthState {
  userId: string | null
  tenantId: string | null
  role: Role | null
  phoneNumber: string | null
  isAuthenticated: boolean
  loading: boolean
  requestOtp: (phone: string) => Promise<void>
  verifyOtp: (phone: string, code: string, displayName?: string, tenantName?: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [claims, setClaims] = useState<JwtClaims | null>(null)
  const [phoneNumber, setPhoneNumber] = useState<string | null>(localStorage.getItem('teacheros_phone'))
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('teacheros_access_token')
    if (token) {
      const decoded = decodeJwt(token)
      if (decoded && decoded.exp * 1000 > Date.now()) {
        setClaims(decoded)
      } else {
        clearTokens()
      }
    }
    setLoading(false)
  }, [])

  async function requestOtp(phone: string) {
    await api.post<void>('/api/v1/auth/otp/request', { phoneNumber: phone }, false)
    setPhoneNumber(phone)
    localStorage.setItem('teacheros_phone', phone)
  }

  async function verifyOtp(phone: string, code: string, displayName?: string, tenantName?: string) {
    const tokens = await api.post<TokenResponse>(
      '/api/v1/auth/otp/verify',
      { phoneNumber: phone, code, displayName, tenantName },
      false
    )
    setTokens(tokens.accessToken, tokens.refreshToken)
    const decoded = decodeJwt(tokens.accessToken)
    setClaims(decoded)
  }

  function logout() {
    clearTokens()
    localStorage.removeItem('teacheros_phone')
    setClaims(null)
    setPhoneNumber(null)
  }

  const value: AuthState = {
    userId: claims?.sub ?? null,
    tenantId: claims?.tenantId ?? null,
    role: claims?.role ?? null,
    phoneNumber,
    isAuthenticated: !!claims,
    loading,
    requestOtp,
    verifyOtp,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
