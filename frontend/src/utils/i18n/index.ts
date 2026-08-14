import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import sr from '@shared/lang/sr.json'
import { formatDate, type DatePreset } from '@utils/format'
import { DEFAULT_LANGUAGE, LANGUAGE_CODES, intlLocale } from './locales'

i18n
    .use(initReactI18next)
    .init({
        lng: DEFAULT_LANGUAGE,
        fallbackLng: DEFAULT_LANGUAGE,
        supportedLngs: LANGUAGE_CODES,
        resources: {
            sr: {
                translation: sr,
            },
        },
        interpolation: {
            escapeValue: false,
        },
    })

i18n.services.formatter?.add('date', (value, lng, options) =>
    formatDate(value, intlLocale(lng), options.preset as DatePreset | undefined),
)

if (import.meta.hot) {
    import.meta.hot.accept('@shared/lang/sr.json', (mod) => {
        if (!mod) return
        i18n.removeResourceBundle('sr', 'translation')
        i18n.addResourceBundle('sr', 'translation', mod.default, true, true)
        i18n.emit('languageChanged', i18n.language)
    })
}

export default i18n
