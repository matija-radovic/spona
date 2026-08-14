/** The key names an i18next bundle; `intl` needs the script segment, as plain `sr` is Cyrillic. */
export const LANGUAGES = {
  sr: { intl: 'sr-Latn-RS', label: 'Srpski' },
} as const

export type LanguageCode = keyof typeof LANGUAGES

export const DEFAULT_LANGUAGE: LanguageCode = 'sr'

export const LANGUAGE_CODES = Object.keys(LANGUAGES) as LanguageCode[]

const isLanguageCode = (value: string): value is LanguageCode => Object.hasOwn(LANGUAGES, value)

/** Region suffixes (`sr-RS`) are stripped; unknown codes fall back to the default language. */
export const intlLocale =(language: string | undefined): string => {
  const base = (language ?? '').split('-')[0]
  return LANGUAGES[isLanguageCode(base) ? base : DEFAULT_LANGUAGE].intl
}
