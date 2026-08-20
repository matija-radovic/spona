import './RouteLoader.scss'
import { useTranslation } from 'react-i18next'

const RouteLoader = () => {
  const { t } = useTranslation()

  return (
    <div className="route-loader" role="status" aria-live="polite">
      <span className="route-loader-spinner" aria-hidden />
      <span className="route-loader-text">{t('common.loading')}</span>
    </div>
  )
}

export default RouteLoader
