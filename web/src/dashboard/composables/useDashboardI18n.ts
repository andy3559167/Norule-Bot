import { reactive } from 'vue'
import type { I18nPayload, LanguageOption } from '../types/dashboard'
import { dashboardApi } from './useDashboardApi'

const FALLBACK_LANGUAGES: LanguageOption[] = [
  { code: 'zh-TW', label: '繁體中文' },
  { code: 'zh-CN', label: '简体中文' },
  { code: 'en', label: 'English' },
]

const state = reactive({
  ready: false,
  language: 'zh-TW',
  defaultLanguage: 'zh-TW',
  uiLanguages: [...FALLBACK_LANGUAGES],
  botLanguages: [...FALLBACK_LANGUAGES],
  bundles: {} as Record<string, Record<string, string>>,
})

function translate(key: string, fallback = key, replacements?: Record<string, string | number>) {
  const localized = state.bundles[state.language]?.[key]
    || state.bundles.en?.[key]
    || fallback
  if (!replacements) return localized
  return Object.entries(replacements).reduce(
    (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
    localized,
  )
}

function bundleText(language: string, key: string) {
  return state.bundles[language]?.[key] || ''
}

function setLanguage(language: string) {
  const supported = state.uiLanguages.some((item) => item.code === language)
  state.language = supported ? language : state.defaultLanguage
  if (import.meta.client) {
    localStorage.setItem('norule.web.ui.lang', state.language)
    document.documentElement.lang = state.language
  }
}

async function initializeI18n() {
  try {
    const payload = await dashboardApi<I18nPayload>('/api/web/i18n')
    state.defaultLanguage = payload.defaultLanguage || 'zh-TW'
    state.uiLanguages = payload.uiLanguages?.length ? payload.uiLanguages : [...FALLBACK_LANGUAGES]
    state.botLanguages = payload.botLanguages?.length ? payload.botLanguages : [...FALLBACK_LANGUAGES]
    state.bundles = payload.bundles || {}
  } catch {
    state.uiLanguages = [...FALLBACK_LANGUAGES]
    state.botLanguages = [...FALLBACK_LANGUAGES]
  }
  const preferred = import.meta.client ? localStorage.getItem('norule.web.ui.lang') : null
  setLanguage(preferred || state.defaultLanguage)
  state.ready = true
}

export function useDashboardI18n() {
  return {
    state,
    t: translate,
    bundleText,
    setLanguage,
    initializeI18n,
  }
}
