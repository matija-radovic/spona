import './Footer.scss'
import { useTranslation } from 'react-i18next'
import { Logo, LogoSize } from '@shared/components/Logo'
import { Link, useLocation } from 'react-router-dom'
import { FOOTER_LINKS } from './footerLinks'

const Footer = () => {
  const { t } = useTranslation();
  const year = new Date().getFullYear();

  const location = useLocation();
  const isAuthPage = ['/login', '/register'].includes(location.pathname);

  return (
    <footer className="footer">
      <div className="footer-inner container">
        <ul className="footer-links">
          {isAuthPage &&
            <li key='go-home'>
              <Link to='/' className="footer-link">
                {t('nav.home')}
              </Link>
            </li>
          }
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
