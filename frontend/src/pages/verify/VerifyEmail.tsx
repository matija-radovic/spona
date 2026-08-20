import './VerifyEmail.scss'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconAlertTriangle, IconCircleCheck } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components/Button'
import { verifyEmail } from '@utils/api/auth'

type Status = 'verifying' | 'success' | 'error'

/** Consumes an emailed verification link: `/verify/:token` */
const VerifyEmail = () => {
  const { t } = useTranslation()
  const { token } = useParams<{ token: string }>()
  const [status, setStatus] = useState<Status>('verifying')

  useEffect(() => {
    let cancelled = false
    verifyEmail(token ?? '')
      .then(() => {
        if (!cancelled) setStatus('success')
      })
      .catch(() => {
        if (!cancelled) setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <div className="verify">
      <div className="verify-inner">
        {status === 'verifying' && (
          <>
            <span className="verify-spinner" aria-hidden />
            <p className="verify-text">{t('verify.verifying')}</p>
          </>
        )}

        {status === 'success' && (
          <>
            <IconCircleCheck className="verify-icon verify-icon-ok" aria-hidden />
            <h1 className="verify-title">{t('verify.successTitle')}</h1>
            <p className="verify-text">{t('verify.successBody')}</p>
            <Button to="/login" variant={ButtonVariant.PRIMARY}>
              {t('verify.toLogin')}
            </Button>
          </>
        )}

        {status === 'error' && (
          <>
            <IconAlertTriangle className="verify-icon verify-icon-error" aria-hidden />
            <h1 className="verify-title">{t('verify.errorTitle')}</h1>
            <p className="verify-text">{t('verify.errorBody')}</p>
            <Button to="/login" variant={ButtonVariant.PRIMARY}>
              {t('verify.toLogin')}
            </Button>
          </>
        )}
      </div>
    </div>
  )
}

export default VerifyEmail
