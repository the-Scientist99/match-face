import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './locales/en.json'
import me from './locales/me.json'

export type Lang = 'me' | 'en'

const stored = localStorage.getItem('lang')
const initial: Lang =
  stored === 'me' || stored === 'en' ? stored : navigator.language.toLowerCase().startsWith('en') ? 'en' : 'me'

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, me: { translation: me } },
  lng: initial,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (lng) => {
  localStorage.setItem('lang', lng)
  document.documentElement.lang = lng
})
document.documentElement.lang = initial

export default i18n
