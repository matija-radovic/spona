import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { AuthCard, FieldShell, Input } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { PASSWORD_MIN, PASSWORD_MAX } from '@utils/constants/validation'
import { confirmPasswordReset } from '@utils/api/auth'
import { buildConfirmResetSchema, type ConfirmResetData } from './schema'

type Status = 'form' | 'success' | 'error'

const ConfirmPasswordReset = () => {
  const { t } = useTranslation()
  const { token } = useParams<{ token: string }>()
  const [status, setStatus] = useState<Status>('form')

  const schema = useMemo(() => buildConfirmResetSchema(t), [t])
  const {
    register,
    handleSubmit,
    formState: { errors, dirtyFields, isSubmitting },
  } = useForm<ConfirmResetData>({ resolver: zodResolver(schema), mode: 'onChange' })

  const onSubmit = handleSubmit(async ({ password }) => {
    try {
      await confirmPasswordReset(token ?? '', password)
      setStatus('success')
    } catch {
      setStatus('error')
    }
  })

  if (status === 'success') {
    return (
      <AuthCard
        title={t('reset.successTitle')}
        subtitle={t('reset.successBody')}
        footer={
          <Button to="/login" variant={ButtonVariant.PRIMARY}>
            {t('reset.backToLogin')}
          </Button>
        }
      />
    )
  }

  if (status === 'error') {
    return (
      <AuthCard
        title={t('reset.errorTitle')}
        subtitle={t('reset.errorBody')}
        footer={
          <Button to="/reset" variant={ButtonVariant.PRIMARY}>
            {t('reset.requestAgain')}
          </Button>
        }
      />
    )
  }

  return (
    <AuthCard title={t('reset.confirmTitle')} subtitle={t('reset.confirmSubtitle')}>
      <form onSubmit={onSubmit} noValidate>
        <FieldShell
          label={t('reset.newPassword')}
          hint={t('auth.passwordHelper', { min: PASSWORD_MIN, max: PASSWORD_MAX })}
        >
          <Input
            type="password"
            autoComplete="new-password"
            error={errors.password?.message}
            valid={!!dirtyFields.password && !errors.password}
            {...register('password')}
          />
        </FieldShell>

        <FieldShell label={t('reset.confirmPassword')}>
          <Input
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            valid={!!dirtyFields.confirmPassword && !errors.confirmPassword}
            {...register('confirmPassword')}
          />
        </FieldShell>
        <div className="auth-card-actions">
          <Button to="/login" variant={ButtonVariant.GHOST}>
            {t('reset.backToLogin')}
          </Button>
          <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
            {isSubmitting ? t('common.loading') : t('reset.confirmSubmit')}
          </Button>
        </div>
      </form>
    </AuthCard>
  )
}

export default ConfirmPasswordReset
