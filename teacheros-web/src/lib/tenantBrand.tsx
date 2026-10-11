import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from './api'
import { useAuth } from './auth'
import type { TenantSettingsResponse } from './types'

// Logo URLs are signed and expire after 15 minutes (see StorageService), so
// refetch well inside that window to keep a long-open tab from showing a
// dead image.
const REFRESH_MS = 10 * 60 * 1000

interface TenantBrandState {
  logoUrl: string | null
  refresh: () => Promise<void>
}

const TenantBrandContext = createContext<TenantBrandState>({ logoUrl: null, refresh: async () => {} })

export function TenantBrandProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const [logoUrl, setLogoUrl] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const tenant = await api.get<TenantSettingsResponse>('/api/v1/tenant')
      setLogoUrl(tenant.logoUrl)
    } catch {
      // Branding is cosmetic - keep whatever we had; the "T" badge covers a miss.
    }
  }, [])

  useEffect(() => {
    if (!isAuthenticated) {
      setLogoUrl(null)
      return
    }
    refresh()
    const id = setInterval(refresh, REFRESH_MS)
    return () => clearInterval(id)
  }, [isAuthenticated, refresh])

  return <TenantBrandContext.Provider value={{ logoUrl, refresh }}>{children}</TenantBrandContext.Provider>
}

export function useTenantBrand() {
  return useContext(TenantBrandContext)
}
