export type RequestStatus = 'idle' | 'loading' | 'success' | 'error' | 'disabled'

export interface ApiErrorResponse {
  error?: string
  errorCode?: string
  status?: number
}

export interface ShortUrlResponse {
  code: string
  shortUrl: string
  targetUrl: string
  viewCount: number
}

export interface MediaShareConfig {
  enabled: boolean
  defaultRetentionHours: number
  maxRetentionDays: number
  maxFileSizeBytes: number
  maxFileSizeMb: number
  maxVideoFileSizeBytes: number
  maxVideoFileSizeMb: number
  maxVideoDurationSeconds: number
  expiredShareRetentionDays: number
}

export interface MediaShareResponse {
  code: string
  shortUrl: string
  expiresAt: number
  passwordProtected: boolean
  viewCount: number
}

export interface MediaUploadInput {
  file: File
  retentionMinutes?: number
  expiresAt?: number
  passwordProtected: boolean
  password?: string
}
