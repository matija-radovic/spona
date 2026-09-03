import './PlaceholderPage.scss'
import { useTranslation } from 'react-i18next'
import { IconTool } from '@tabler/icons-react'

/**
 * Dumb "coming soon" shell for routes whose real page ships in a later revision
 * (feed, post page, job creation, settings). Kept intentionally minimal.
 */
const PlaceholderPage = () => {
  const { t } = useTranslation()

  return (
    <div className="placeholder-page container">
      <IconTool aria-hidden />
      <p className="placeholder-page-text">{t('common.comingSoon')}</p>
    </div>
  )
}

export default PlaceholderPage
