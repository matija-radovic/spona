import type { UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { FieldShell, Input } from '@shared/components'
import {
  USERNAME_MIN,
  USERNAME_MAX,
  PASSWORD_MIN,
  PASSWORD_MAX,
} from '@utils/constants/validation'
import type { RegisterData } from '../schema'

interface StageAccountProps {
  form: UseFormReturn<RegisterData>
  /** True once a "Next" uniqueness check found the username taken. */
  usernameTaken: boolean
}

const StageAccount = ({ form, usernameTaken }: StageAccountProps) => {
  const { t } = useTranslation()
  const {
    register,
    formState: { errors, dirtyFields },
  } = form

  const usernameError = usernameTaken ? t('errors.usernameTaken') : errors.username?.message

  return (
    <div className="register-fields">
      <FieldShell
        label={t('auth.username')}
        required
        hint={t('auth.usernameHelper', { min: USERNAME_MIN, max: USERNAME_MAX })}
      >
        <Input
          autoComplete="username"
          error={usernameError}
          valid={!!dirtyFields.username && !errors.username && !usernameTaken}
          {...register('username')}
        />
      </FieldShell>

      <FieldShell label={t('auth.email')} required>
        <Input
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          valid={!!dirtyFields.email && !errors.email}
          {...register('email')}
        />
      </FieldShell>

      <FieldShell
        label={t('auth.password')}
        required
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

      <FieldShell label={t('auth.confirmPassword')} required>
        <Input
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          valid={!!dirtyFields.confirmPassword && !errors.confirmPassword}
          {...register('confirmPassword')}
        />
      </FieldShell>
    </div>
  )
}

export default StageAccount
