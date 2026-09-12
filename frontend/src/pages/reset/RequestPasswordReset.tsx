import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { AuthCard, FieldShell, Input } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { requestPasswordReset } from '@utils/api/auth'
import { parseProblem } from '@utils/api/types'
import { buildRequestResetSchema, type RequestResetData } from './schema'

const RequestPasswordReset = () => {
  const { t } = useTranslation()
  const [sent, setSent] = useState(false)

  const schema = useMemo(() => buildRequestResetSchema(t), [t])
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RequestResetData>({ resolver: zodResolver(schema), mode: 'onChange' })

  const onSubmit = handleSubmit(async ({ email }) => {
    try {
      await requestPasswordReset(email)
      setSent(true)
    } catch (caught) {
      const { status } = parseProblem(caught)
      setError('email', {
        message: status === 422 ? t('errors.invalidEmail') : t('errors.serverError'),
      })
    }
  })

  if (sent) {
    return (
      <AuthCard
        title={t('reset.requestSentTitle')}
        subtitle={t('reset.requestSentBody')}
        footer={
          <Button to="/login" variant={ButtonVariant.PRIMARY}>
            {t('reset.backToLogin')}
          </Button>
        }
      />
    )
  }

  return (
    <AuthCard title={t('reset.requestTitle')} subtitle={t('reset.requestSubtitle')}>
      <form onSubmit={onSubmit} noValidate>
        <FieldShell label={t('auth.email')}>
          <Input
            type="email"
            autoComplete="email"
            error={errors.email?.message}
            {...register('email')}
          />
        </FieldShell>
        <div className="auth-card-actions">
          <Button to="/login" variant={ButtonVariant.GHOST}>
            {t('reset.backToLogin')}
          </Button>
          <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
            {isSubmitting ? t('common.loading') : t('reset.requestSubmit')}
          </Button>
        </div>
      </form>
    </AuthCard>
  )
}

export default RequestPasswordReset
