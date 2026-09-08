import './RailLegal.scss'
import { useTranslation } from 'react-i18next'
import { FOOTER_LINKS } from '@shared/components/chrome/Footer/footerLinks'

/** The footer links meant for rail */
const RailLegal = () => {
  const { t } = useTranslation()
  const year = new Date().getFullYear()

  return (
    <section className="rail-legal">
      <ul className="rail-legal-links">
        {FOOTER_LINKS.map(({ labelKey, href }) => (
          <li key={labelKey}>
            <a href={href} className="rail-legal-link">
              {t(labelKey)}
            </a>
          </li>
        ))}
      </ul>
      <p className="rail-legal-copy">{t('footer.copyright', { year })}</p>
    </section>
  )
}

export default RailLegal
