import { useMemo, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant, TextField } from '@shared/components'
import useTimedFlag from '@shared/hooks/useTimedFlag'
import { parseProblem } from '@utils/api/types'
import { requestPasswordReset } from '@utils/api/auth'
import { changePassword } from '@utils/api/settings'
import { PASSWORD_MAX, PASSWORD_MIN } from '@utils/constants/validation'
import { CredentialForm } from '../CredentialForm'
import { buildPasswordSchema, PASSWORD_DEFAULTS, type PasswordData } from '../schema'

interface PasswordFormProps {
  /** The account address the reset link is sent to. */
  email: string
}

const PasswordForm = ({ email }: PasswordFormProps) => {
  const { t } = useTranslation()
  const { active: justSaved, raise: markSaved } = useTimedFlag()

  const [notice, setNotice] = useState<{ ok: boolean; text: string } | null>(null)
  const [sendingReset, setSendingReset] = useState(false)

  const schema = useMemo(() => buildPasswordSchema(t), [t])
  const {
    register,
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<PasswordData>({ resolver: zodResolver(schema), defaultValues: PASSWORD_DEFAULTS })

  const [currentPassword, newPassword, repeatPassword] = useWatch({
    control,
    name: ['currentPassword', 'newPassword', 'repeatPassword'],
  })
  const allFilled = Boolean(currentPassword && newPassword && repeatPassword)

  const onSubmit = handleSubmit(async (data) => {
    try {
      await changePassword({ currentPassword: data.currentPassword, newPassword: data.newPassword })
      reset(PASSWORD_DEFAULTS)
      setNotice(null)
      markSaved()
    } catch (caught) {
      const problem = parseProblem(caught)
      const message = problem.detail || t('errors.serverError')
      setError('currentPassword', { message })
      setError('root', { message })
    }
  })

  const sendReset = async () => {
    setSendingReset(true)
    setNotice(null)
    try {
      await requestPasswordReset(email)
      setNotice({ ok: true, text: t('settings.account.password.resetSent', { email }) })
    } catch (caught) {
      setNotice({ ok: false, text: parseProblem(caught).detail || t('errors.serverError') })
    } finally {
      setSendingReset(false)
    }
  }

  return (
    <CredentialForm
      onSubmit={onSubmit}
      submitLabel={t('settings.account.password.submit')}
      busy={isSubmitting}
      disabled={!allFilled}
      error={errors.root?.message}
      success={justSaved ? t('settings.account.password.success') : undefined}
      aside={
        <Button variant={ButtonVariant.GHOST} onClick={sendReset} disabled={sendingReset}>
          {t('auth.forgotPassword')}
        </Button>
      }
    >
      <TextField
        label={t('settings.account.password.current')}
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...register('currentPassword')}
      />

      <TextField
        label={t('settings.account.password.new')}
        type="password"
        autoComplete="new-password"
        helper={t('auth.passwordHelper', { min: PASSWORD_MIN, max: PASSWORD_MAX })}
        error={errors.newPassword?.message}
        {...register('newPassword')}
      />

      <TextField
        label={t('settings.account.password.repeat')}
        type="password"
        autoComplete="new-password"
        error={errors.repeatPassword?.message}
        {...register('repeatPassword')}
      />

      {notice && (
        <p className={notice.ok ? 'settings-flash' : 'credential-form-error'}>{notice.text}</p>
      )}
    </CredentialForm>
  )
}

export default PasswordForm
