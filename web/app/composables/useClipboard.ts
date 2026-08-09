import { readonly, ref } from 'vue'

function copyWithSelection(value: string): boolean {
  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  textarea.remove()
  return copied
}

async function writeToClipboard(value: string): Promise<void> {
  if (!navigator.clipboard?.writeText) {
    if (!copyWithSelection(value)) throw new Error('Clipboard API unavailable')
    return
  }
  let timeoutId: ReturnType<typeof setTimeout> | undefined
  try {
    await Promise.race([
      navigator.clipboard.writeText(value),
      new Promise<never>((_, reject) => {
        timeoutId = setTimeout(() => reject(new Error('Clipboard API timed out')), 900)
      }),
    ])
  } catch {
    if (!copyWithSelection(value)) throw new Error('Clipboard copy failed')
  } finally {
    clearTimeout(timeoutId)
  }
}

export function useClipboard(resetAfter = 1400) {
  const state = ref<'idle' | 'copying' | 'copied' | 'error'>('idle')
  const error = ref('')
  let resetTimer: ReturnType<typeof setTimeout> | undefined

  async function copy(value: string) {
    if (!value || state.value === 'copying') return false
    state.value = 'copying'
    error.value = ''
    try {
      await writeToClipboard(value)
      state.value = 'copied'
      clearTimeout(resetTimer)
      resetTimer = setTimeout(() => { state.value = 'idle' }, resetAfter)
      return true
    } catch {
      state.value = 'error'
      error.value = '無法存取剪貼簿，請手動複製網址。'
      return false
    }
  }

  return { state: readonly(state), error: readonly(error), copy }
}
