import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { intlLocale } from '@utils/i18n/locales'
import { formatDate, formatNumber, type DatePreset } from '@utils/format'

const useFormat = () => {
  const { i18n } = useTranslation()
  const locale = intlLocale(i18n.resolvedLanguage ?? i18n.language)

  return useMemo(
    () => ({
      locale,
      date: (iso: string | null | undefined, preset: DatePreset = 'long') =>
        formatDate(iso, locale, preset),
      number: (value: number) => formatNumber(value, locale),
    }),
    [locale],
  )
}

export default useFormat
