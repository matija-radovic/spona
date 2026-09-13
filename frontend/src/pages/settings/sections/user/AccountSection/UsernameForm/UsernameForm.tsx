import { useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { FieldShell, Input } from '@shared/components'
import { useUser } from '@shared/contexts'
import useTimedFlag from '@shared/hooks/useTimedFlag'
import { parseProblem } from '@utils/api/types'
import { changeUsername, type UserSettingsDto } from '@utils/api/settings'
import { USERNAME_MAX, USERNAME_MIN } from '@utils/constants/validation'
import { CredentialForm } from '../CredentialForm'
import { buildUsernameSchema, type UsernameData } from '../schema'

interface UsernameFormProps {
  settings: UserSettingsDto
  refetch: () => Promise<void>
}

const UsernameForm = ({ settings, refetch }: UsernameFormProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user, updateUser } = useUser()
  const { active: justSaved, raise: markSaved } = useTimedFlag()

  const schema = useMemo(() => buildUsernameSchema(t), [t])
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<UsernameData>({
    resolver: zodResolver(schema),
    defaultValues: { username: settings.username },
  })

  const onSubmit = handleSubmit(async (values) => {
    try {
      const updated = await changeUsername(settings.username, values)
      reset({ username: updated.username })
      markSaved()
      if (user) updateUser({ ...user, username: updated.username })
      await refetch()
      navigate(`/users/${updated.username}/settings/account`, { replace: true })
    } catch (caught) {
      const problem = parseProblem(caught)
      const message = problem.detail || t('errors.serverError')
      setError('username', { message })
      setError('root', { message })
    }
  })

  return (
    <CredentialForm
      onSubmit={onSubmit}
      submitLabel={t('settings.account.username.submit')}
      busy={isSubmitting}
      disabled={!isDirty}
      error={errors.root?.message}
      success={justSaved ? t('settings.account.username.success') : undefined}
    >
      <FieldShell
        label={t('auth.username')}
        hint={t('settings.account.username.helper', { min: USERNAME_MIN, max: USERNAME_MAX })}
      >
        <Input
          autoComplete="username"
          error={errors.username?.message}
          {...register('username')}
        />
      </FieldShell>
    </CredentialForm>
  )
}

export default UsernameForm
