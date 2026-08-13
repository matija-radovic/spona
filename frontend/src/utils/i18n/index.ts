import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import sr from '@shared/lang/sr.json'

i18n
    .use(initReactI18next)
    .init({
        lng: 'sr',
        fallbackLng: 'sr',
        resources: {
            sr: {
                translation: sr,
            },
        },
        interpolation: {
            escapeValue: false,
        },
    })

if (import.meta.hot) {
    import.meta.hot.accept('@shared/lang/sr.json', (mod) => {
        if (!mod) return
        i18n.removeResourceBundle('sr', 'translation')
        i18n.addResourceBundle('sr', 'translation', mod.default, true, true)
        i18n.emit('languageChanged', i18n.language)
    })
}

export default i18n
