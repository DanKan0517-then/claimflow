const TOKEN_KEY = 'claimflow.token'

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

export const tokenStore = {
  get: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set: (token: string) => {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* private mode: session-only login */
    }
  },
  clear: () => {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

type Body = Record<string, unknown> | FormData | undefined

/**
 * Cold starts: on free hosting the API sleeps when idle. While it boots, nginx answers 502/503.
 * Those mean the request never reached the app, so it is safe to retry. We retry for up to
 * ~3 minutes and tell the UI so it can show a "waking up" banner instead of an error.
 */
const WAKE_RETRY_MS = 3000
const WAKE_MAX_MS = 180_000
type WakeListener = (waking: boolean) => void
const wakeListeners = new Set<WakeListener>()
let wakingCount = 0
function setWaking(delta: number) {
  const before = wakingCount > 0
  wakingCount = Math.max(0, wakingCount + delta)
  const after = wakingCount > 0
  if (before !== after) wakeListeners.forEach((l) => l(after))
}
export function onServerWaking(fn: WakeListener) {
  wakeListeners.add(fn)
  return () => {
    wakeListeners.delete(fn)
  }
}
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

async function fetchWithWake(url: string, init: RequestInit): Promise<Response> {
  const started = Date.now()
  let waiting = false
  try {
    for (;;) {
      let res: Response | null = null
      try {
        res = await fetch(url, init)
      } catch {
        res = null // network error while the server restarts
      }
      const unavailable = res === null || res.status === 502 || res.status === 503
      if (!unavailable || Date.now() - started > WAKE_MAX_MS) {
        if (res) return res
        throw new ApiError(0, 'Could not reach the server. Check your connection.')
      }
      if (!waiting) {
        waiting = true
        setWaking(1)
      }
      await sleep(WAKE_RETRY_MS)
    }
  } finally {
    if (waiting) setWaking(-1)
  }
}

async function request<T>(method: string, path: string, body?: Body): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }

  const res = await fetchWithWake(`/api${path}`, { method, headers, body: payload })
  if (res.status === 401 && token) {
    onUnauthorized()
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    let fieldErrors: Record<string, string> = {}
    try {
      const problem = await res.json()
      if (problem?.detail) message = problem.detail
      if (problem?.errors) fieldErrors = problem.errors
    } catch {
      if (res.status === 429) message = 'Too many requests, slow down a little'
      if (res.status === 502 || res.status === 503) message = 'The server is taking longer than usual to start. Please try again.'
    }
    throw new ApiError(res.status, message, fieldErrors)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: Body) => request<T>('POST', path, body),
  patch: <T>(path: string, body?: Body) => request<T>('PATCH', path, body),
}

/** Downloads need the auth header, so fetch as a blob and open it. */
export async function openDocument(claimId: number, docId: number) {
  const token = tokenStore.get()
  const res = await fetchWithWake(`/api/claims/${claimId}/documents/${docId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) throw new ApiError(res.status, 'Could not open document')
  const url = URL.createObjectURL(await res.blob())
  window.open(url, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export function qs(params: Record<string, string | number | undefined | null>) {
  const s = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') s.set(k, String(v))
  }
  const str = s.toString()
  return str ? `?${str}` : ''
}

/** Fire-and-forget request that starts waking the API as soon as the page loads. */
export function warmUp() {
  fetch('/healthz').catch(() => {})
}
