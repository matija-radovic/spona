import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/form/Button'

const HeaderGuestActions = () => {
  const { t } = useTranslation()
  return (
    <div className="header-auth">
      <Button to="/login" variant={ButtonVariant.SECONDARY}>
        {t('nav.signIn')}
      </Button>
      <Button to="/register" variant={ButtonVariant.PRIMARY}>
        {t('nav.registerNow')}
      </Button>
    </div>
  )
}

export default HeaderGuestActions
