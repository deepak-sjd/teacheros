import { type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, loading } = useAuth()

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-paper">
        <p className="text-slate text-sm">Loading…</p>
      </div>
    )
  }

  if (!isAuthenticated) return <Navigate to="/login" replace />

  return <>{children}</>
}
