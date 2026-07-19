import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/Button'

const MobileGuestActions = () => {
  const { t } = useTranslation()
  return (
    <div className="mobile-nav-auth">
      <Button to="/login" variant={ButtonVariant.SECONDARY} fullWidth>
        {t('nav.signIn')}
      </Button>
      <Button to="/register" variant={ButtonVariant.PRIMARY} fullWidth>
        {t('nav.registerNow')}
      </Button>
    </div>
  )
}

export default MobileGuestActions
