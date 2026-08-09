import type { ApiErrorResponse } from '~/types/api'

export class ApiRequestError extends Error {
  readonly payload: ApiErrorResponse

  constructor(payload: ApiErrorResponse, fallback: string) {
    super(payload.error?.trim() || fallback)
    this.name = 'ApiRequestError'
    this.payload = payload
  }
}

export async function readJson<T>(response: Response): Promise<T | null> {
  try {
    return (await response.json()) as T
  } catch {
    return null
  }
}

export function normalizeError(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}
