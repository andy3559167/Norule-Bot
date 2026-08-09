import { readonly, ref } from 'vue'
import type { ApiErrorResponse, MediaShareConfig, MediaShareResponse, MediaUploadInput, RequestStatus } from '~/types/api'
import { DEFAULT_MEDIA_CONFIG, mediaErrorMessage } from '~/utils/media'

function parsePayload<T>(text: string): T | null {
  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}

export function useMediaShare() {
  const config = ref<MediaShareConfig>({ ...DEFAULT_MEDIA_CONFIG })
  const configLoading = ref(true)
  const status = ref<RequestStatus>('idle')
  const progress = ref<number | null>(null)
  const result = ref<MediaShareResponse | null>(null)
  const error = ref('')

  async function loadConfig() {
    configLoading.value = true
    try {
      const response = await fetch('/api/short/image/config')
      if (response.ok) {
        const payload = await response.json() as Partial<MediaShareConfig>
        config.value = { ...DEFAULT_MEDIA_CONFIG, ...payload }
      }
      if (!config.value.enabled) status.value = 'disabled'
    } catch {
      error.value = '無法取得媒體分享設定，暫時使用預設限制。'
    } finally {
      configLoading.value = false
    }
  }

  function upload(input: MediaUploadInput): Promise<void> {
    status.value = 'loading'
    progress.value = null
    result.value = null
    error.value = ''

    const formData = new FormData()
    formData.append('image', input.file)
    if (input.expiresAt) formData.append('expiresAt', String(input.expiresAt))
    else if (input.retentionMinutes) formData.append('retentionMinutes', String(input.retentionMinutes))
    formData.append('passwordProtected', String(input.passwordProtected))
    if (input.passwordProtected && input.password) formData.append('password', input.password)

    return new Promise((resolve) => {
      const request = new XMLHttpRequest()
      request.open('POST', '/api/short/image')
      request.upload.onprogress = (event) => {
        progress.value = event.lengthComputable ? Math.round((event.loaded / event.total) * 100) : null
      }
      request.onload = () => {
        const payload = parsePayload<MediaShareResponse & ApiErrorResponse>(request.responseText)
        if (request.status < 200 || request.status >= 300 || !payload?.shortUrl) {
          const syntheticCode = [502, 503, 504].includes(request.status)
            ? 'MEDIA_GATEWAY_FAILED'
            : request.status === 413 ? 'MEDIA_TOO_LARGE' : payload?.errorCode
          error.value = mediaErrorMessage({ ...payload, errorCode: syntheticCode, status: request.status }, config.value)
          status.value = 'error'
        } else {
          result.value = payload
          progress.value = 100
          status.value = 'success'
        }
        resolve()
      }
      request.onerror = () => {
        error.value = '網路連線失敗，媒體尚未完成上傳。'
        status.value = 'error'
        resolve()
      }
      request.onabort = () => {
        error.value = '媒體上傳已取消。'
        status.value = 'error'
        resolve()
      }
      request.send(formData)
    })
  }

  return {
    config: readonly(config),
    configLoading: readonly(configLoading),
    status: readonly(status),
    progress: readonly(progress),
    result: readonly(result),
    error: readonly(error),
    loadConfig,
    upload,
  }
}
