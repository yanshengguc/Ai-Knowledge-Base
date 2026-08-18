import { createI18n } from 'vue-i18n'
import zh from '@/locales/zh'
import en from '@/locales/en'

// 语言偏好记忆(localStorage)
const saved = localStorage.getItem('akb_lang')
const locale = saved === 'en' ? 'en' : 'zh'

const i18n = createI18n({
  legacy: false,
  locale,
  fallbackLocale: 'zh',
  messages: { zh, en },
})

export function setLocale(lang: 'zh' | 'en') {
  ;(i18n.global.locale as unknown as { value: string }).value = lang
  localStorage.setItem('akb_lang', lang)
}

export default i18n
