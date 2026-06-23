import './Footer.scss'
import { useTranslation } from 'react-i18next'
import { Logo } from '@shared/components/Logo'
import { LogoSize } from '../Logo/Logo'

interface FooterLink {
  labelKey: string
  href: string
}

const FOOTER_LINKS: readonly FooterLink[] = [
  { labelKey: 'footer.about', href: '#' },
  { labelKey: 'footer.cookiePolicy', href: '#' },
  { labelKey: 'footer.privacyPolicy', href: '#' },
  { labelKey: 'footer.userAgreement', href: '#' },
  { labelKey: 'footer.license', href: 'https://opensource.org/licenses/MIT' },
]

const Footer = () => {
  const { t } = useTranslation()
  const year = new Date().getFullYear()

  return (
    <footer className="footer">
      <div className="footer-inner container">
        <ul className="footer-links">
          {FOOTER_LINKS.map(({ labelKey, href }) => (
            <li key={labelKey}>
              <a href={href} className="footer-link">
                {t(labelKey)}
              </a>
            </li>
          ))}
        </ul>

        <div className="footer-brand">
          <Logo size={LogoSize.TINY} asLink={false} />
          <p className="footer-copy">{t('footer.copyright', { year })}</p>
        </div>
      </div>
    </footer>
  )
}

export default Footer
