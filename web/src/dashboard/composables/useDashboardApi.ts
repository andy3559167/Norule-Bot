interface ApiErrorPayload {
  error?: string
  message?: string
}

export async function dashboardApi<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'application/json')

  const response = await fetch(url, {
    credentials: 'same-origin',
    ...options,
    headers,
  })

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json() as T & ApiErrorPayload
    : null

  if (!response.ok) {
    const errorPayload = payload as ApiErrorPayload | null
    throw new Error(errorPayload?.error || errorPayload?.message || `${response.status} ${response.statusText}`)
  }

  return payload as T
}
