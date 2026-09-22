import { useMemo } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { FieldShell, Input } from '@shared/components'
import useTimedFlag from '@shared/hooks/useTimedFlag'
import { parseProblem } from '@utils/api/types'
import { applyFieldErrors, problemMessage } from '@utils/api/problemMessage'
import { changeEmail, type UserSettingsDto } from '@utils/api/settings'
import { CredentialForm } from '../CredentialForm'
import { buildEmailSchema, type EmailData } from '../schema'

interface EmailFormProps {
  settings: UserSettingsDto
}

const EmailForm = ({ settings }: EmailFormProps) => {
  const { t } = useTranslation()
  const { active: justRequested, raise: markRequested } = useTimedFlag()

  const schema = useMemo(() => buildEmailSchema(t), [t])
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, dirtyFields },
  } = useForm<EmailData>({
    resolver: zodResolver(schema),
    defaultValues: { email: settings.email, currentPassword: '' },
  })

  // The password only confirms the change, so on its own there is nothing to send.
  const currentPassword = useWatch({ control, name: 'currentPassword' })
  const canSubmit = Boolean(dirtyFields.email && currentPassword)

  const onSubmit = handleSubmit(async (values) => {
    try {
      await changeEmail(settings.username, values)
      reset({ email: values.email, currentPassword: '' })
      markRequested()
    } catch (caught) {
      const problem = parseProblem(caught)
      const message = problemMessage(problem, t)
      if (!applyFieldErrors(problem, setError, t)) {
        setError(problem.status === 403 ? 'currentPassword' : 'email', { message })
      }
      setError('root', { message })
    }
  })

  return (
    <CredentialForm
      onSubmit={onSubmit}
      submitLabel={t('settings.account.email.submit')}
      busy={isSubmitting}
      disabled={!canSubmit}
      error={errors.root?.message}
      success={justRequested ? t('settings.account.email.success') : undefined}
    >
      <FieldShell label={t('auth.email')}>
        <Input
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
      </FieldShell>

      <FieldShell label={t('settings.account.email.current')}>
        <Input
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
      </FieldShell>
    </CredentialForm>
  )
}

export default EmailForm
