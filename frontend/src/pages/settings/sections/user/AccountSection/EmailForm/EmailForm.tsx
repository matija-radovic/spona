import { useMemo } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { TextField } from '@shared/components'
import { useUser } from '@shared/contexts'
import useTimedFlag from '@shared/hooks/useTimedFlag'
import { parseProblem } from '@utils/api/types'
import { changeEmail, type UserSettingsDto } from '@utils/api/settings'
import { CredentialForm } from '../CredentialForm'
import { buildEmailSchema, type EmailData } from '../schema'

interface EmailFormProps {
  settings: UserSettingsDto
  refetch: () => Promise<void>
}

const EmailForm = ({ settings, refetch }: EmailFormProps) => {
  const { t } = useTranslation()
  const { user, login } = useUser()
  const { active: justSaved, raise: markSaved } = useTimedFlag()

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
      const updated = await changeEmail(values)
      reset({ email: updated.email, currentPassword: '' })
      markSaved()
      if (user) login({ ...user, email: updated.email })
      await refetch()
    } catch (caught) {
      const problem = parseProblem(caught)
      const message = problem.detail || t('errors.serverError')
      setError(problem.status === 403 ? 'currentPassword' : 'email', { message })
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
      success={justSaved ? t('settings.account.email.success') : undefined}
    >
      <TextField
        label={t('auth.email')}
        type="email"
        autoComplete="email"
        error={errors.email?.message}
        {...register('email')}
      />

      <TextField
        label={t('settings.account.email.current')}
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />
    </CredentialForm>
  )
}

export default EmailForm
