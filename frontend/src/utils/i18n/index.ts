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

export default i18n
