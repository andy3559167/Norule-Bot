import type { RequestStatus } from '~/types/api'

export function resultStatusMessage(status: RequestStatus, progress: number | null, error: string): string {
  if (status === 'loading') return progress === null ? '正在處理，請稍候…' : `上傳進度 ${progress}%`
  if (status === 'disabled') return '此功能目前未開放。'
  if (status === 'error') return error
  return ''
}
