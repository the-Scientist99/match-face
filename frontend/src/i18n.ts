import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { htmlLang, type Lang, pickInitialLang, readStoredLang, storeLang } from './lang'
import en from './locales/en.json'
import me from './locales/me.json'

export type { Lang }

const initial = pickInitialLang(readStoredLang(), navigator.language)

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, me: { translation: me } },
  lng: initial,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (lng) => {
  storeLang(lng as Lang)
  document.documentElement.lang = htmlLang(lng as Lang)
})
document.documentElement.lang = htmlLang(initial)

export default i18n
