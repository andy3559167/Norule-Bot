import { describe, expect, it } from 'vitest'
import { resultStatusMessage } from '../app/utils/result'

describe('ResultCard 狀態文字', () => {
  it('涵蓋 loading、progress、error、disabled 與 success', () => {
    expect(resultStatusMessage('loading', null, '')).toContain('處理')
    expect(resultStatusMessage('loading', 48, '')).toContain('48%')
    expect(resultStatusMessage('error', null, '建立失敗')).toBe('建立失敗')
    expect(resultStatusMessage('disabled', null, '')).toContain('未開放')
    expect(resultStatusMessage('success', null, '')).toBe('')
  })
})
