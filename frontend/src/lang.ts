export type Lang = 'me' | 'en'

const KEY = 'lang'

/** The saved choice wins; otherwise browsers set to English get 'en' and everyone else 'me'. */
export function pickInitialLang(stored: string | null, browserLang: string): Lang {
  if (stored === 'me' || stored === 'en') return stored
  return browserLang.toLowerCase().startsWith('en') ? 'en' : 'me'
}

// Browsers that block site data throw on any localStorage access; the app must still load.
export function readStoredLang(): string | null {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function storeLang(lang: Lang): void {
  try {
    localStorage.setItem(KEY, lang)
  } catch {
    // Not persisted; the choice still applies for this visit.
  }
}

/** Value for <html lang>: 'me' is not a language tag, so Montenegrin uses sr-Latn-ME. */
export function htmlLang(lang: Lang): string {
  return lang === 'me' ? 'sr-Latn-ME' : 'en'
}
