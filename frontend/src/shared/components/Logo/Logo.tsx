import './Logo.scss'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

export const LogoSize = Object.freeze({
  TINY: 'tiny',
  SMALL: 'small',
  MEDIUM: 'medium',
  LARGE: 'large',
} as const)

export type LogoSize = (typeof LogoSize)[keyof typeof LogoSize]

interface LogoProps {
  size?: LogoSize
  asLink?: boolean
}

const Logo = ({ size = LogoSize.SMALL, asLink = true }: LogoProps) => {
  const { t } = useTranslation()

  const className = `logo ${size}`

  const content = (
    <>
      <div className="logo-mark">
        <img src="" alt={t('brand.name')} />
      </div>
      <span className="logo-name">{t('brand.name')}</span>
    </>
  )

  if (asLink) {
    return (
      <Link to="/" className={className} aria-label={t('brand.name')}>
        {content}
      </Link>
    )
  }


  return <span className={className}>{content}</span>
}

export default Logo
