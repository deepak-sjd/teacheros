const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function getAccessToken() {
  return localStorage.getItem('teacheros_access_token')
}

function getRefreshToken() {
  return localStorage.getItem('teacheros_refresh_token')
}

export function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem('teacheros_access_token', accessToken)
  localStorage.setItem('teacheros_refresh_token', refreshToken)
}

export function clearTokens() {
  localStorage.removeItem('teacheros_access_token')
  localStorage.removeItem('teacheros_refresh_token')
}

let refreshPromise: Promise<boolean> | null = null

async function tryRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false

  if (!refreshPromise) {
    refreshPromise = fetch(`${BASE_URL}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return false
        const data = await res.json()
        setTokens(data.accessToken, data.refreshToken)
        return true
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  auth?: boolean
  params?: Record<string, string | number | undefined>
}

async function request<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const { method = 'GET', body, auth = true, params } = options

  let url = `${BASE_URL}${path}`
  if (params) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join('&')
    if (qs) url += `?${qs}`
  }

  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (auth) {
    const token = getAccessToken()
    if (token) headers['Authorization'] = `Bearer ${token}`
  }

  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (res.status === 401 && auth && !retried) {
    const refreshed = await tryRefresh()
    if (refreshed) return request<T>(path, options, true)
    clearTokens()
    window.location.href = '/login'
    throw new ApiError(401, 'Session expired')
  }

  if (!res.ok) {
    let message = res.statusText
    try {
      const data = await res.json()
      message = data.message ?? data.error ?? message
    } catch {
      // no JSON body
    }
    throw new ApiError(res.status, message)
  }

  if (res.status === 202 || res.status === 204) return undefined as T
  return res.json()
}

export const api = {
  get: <T>(path: string, params?: RequestOptions['params']) => request<T>(path, { method: 'GET', params }),
  post: <T>(path: string, body?: unknown, auth = true) => request<T>(path, { method: 'POST', body, auth }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  /**
   * File uploads can't go through request() - that function always
   * JSON.stringifies the body and force-sets Content-Type: application/json,
   * which breaks multipart requests (the browser needs to set its own
   * Content-Type with a generated boundary string for FormData). This is a
   * separate, parallel implementation of the same auth/401-refresh handling
   * rather than a shared one, since the body handling is fundamentally
   * different.
   */
  uploadFile: <T>(path: string, formData: FormData): Promise<T> => uploadMultipart<T>(path, formData),
  /**
   * Downloads a server-generated file (PDF reports/report cards) and saves
   * it via the browser. Can't reuse request() - that always parses the
   * response as JSON - and can't be a plain <a href> either, since the
   * PDF endpoints require an Authorization header the browser has no way
   * to attach to a plain navigation/anchor click.
   */
  downloadFile: (path: string, filename: string) => downloadWithAuth(path, filename),
}

async function downloadWithAuth(path: string, filename: string, retried = false): Promise<void> {
  const url = `${BASE_URL}${path}`
  const headers: Record<string, string> = {}
  const token = getAccessToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(url, { headers })

  if (res.status === 401 && !retried) {
    const refreshed = await tryRefresh()
    if (refreshed) return downloadWithAuth(path, filename, true)
    clearTokens()
    window.location.href = '/login'
    throw new ApiError(401, 'Session expired')
  }

  if (!res.ok) {
    let message = res.statusText
    try {
      const data = await res.json()
      message = data.message ?? data.error ?? message
    } catch {
      // no JSON body (expected here - errors on this endpoint still come back as JSON,
      // but a non-JSON body shouldn't blow up the error path)
    }
    throw new ApiError(res.status, message)
  }

  const blob = await res.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
}

async function uploadMultipart<T>(path: string, formData: FormData, retried = false): Promise<T> {
  const url = `${BASE_URL}${path}`
  const headers: Record<string, string> = {}
  const token = getAccessToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  // Deliberately NOT setting Content-Type here - the browser sets
  // "multipart/form-data; boundary=..." automatically when the body is a
  // FormData instance, and it can only do that correctly if we don't
  // interfere by setting our own Content-Type header.

  const res = await fetch(url, { method: 'POST', headers, body: formData })

  if (res.status === 401 && !retried) {
    const refreshed = await tryRefresh()
    if (refreshed) return uploadMultipart<T>(path, formData, true)
    clearTokens()
    window.location.href = '/login'
    throw new ApiError(401, 'Session expired')
  }

  if (!res.ok) {
    let message = res.statusText
    try {
      const data = await res.json()
      message = data.message ?? data.error ?? message
    } catch {
      // no JSON body
    }
    throw new ApiError(res.status, message)
  }

  return res.json()
}
