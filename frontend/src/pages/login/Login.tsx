import './Login.scss'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { motion } from 'motion/react'
import { FieldShell, Input } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { useUser } from '@shared/contexts'
import { loginRequest } from '@utils/api/auth'
import { parseProblem } from '@utils/api/types'

const buildSchema = (t: (key: string) => string) =>
  z.object({
    identifier: z.string().min(1, t('errors.required')),
    password: z.string().min(1, t('errors.required')),
  })

type LoginData = z.infer<ReturnType<typeof buildSchema>>

const Login = () => {
  const { t } = useTranslation()
  const { login } = useUser()
  const [serverError, setServerError] = useState<string | null>(null)
  const [notVerified, setNotVerified] = useState(false)

  const schema = useMemo(() => buildSchema(t), [t])
  const {
    register,
    handleSubmit,
    formState: { errors, dirtyFields, isSubmitting },
  } = useForm<LoginData>({ resolver: zodResolver(schema), mode: 'onChange' })

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    setNotVerified(false)
    try {
      const { token, user } = await loginRequest(values)
      login(
        {
          id: user.id,
          username: user.username,
          fullName: user.fullName,
          email: user.email,
          avatarUrl: user.avatarUrl ?? '',
        },
        token,
      )
    } catch (caught) {
      if (parseProblem(caught).type === '/problems/email-not-verified') {
        setNotVerified(true)
      } else {
        setServerError(t('errors.loginFailed'))
      }
    }
  })

  return (
    <div className="login">
      <motion.form
        className="login-card"
        onSubmit={onSubmit}
        noValidate
        initial={{ opacity: 0, y: 18 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35, ease: 'easeOut' }}
      >
        <header className="login-head">
          <h2>{t('auth.loginTitle')}</h2>
          <p className="login-sub">{t('auth.loginSubtitle')}</p>
        </header>

        <FieldShell label={t('auth.usernameOrEmail')} required>
          <Input
            autoComplete="username"
            error={errors.identifier?.message}
            valid={!!dirtyFields.identifier && !errors.identifier}
            {...register('identifier')}
          />
        </FieldShell>

        <FieldShell label={t('auth.password')} required>
          <Input
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            valid={!!dirtyFields.password && !errors.password}
            {...register('password')}
          />
        </FieldShell>

        <div className="login-forgot-row">
          <Link to="/reset" className="login-forgot">
            {t('auth.forgotPassword')}
          </Link>
        </div>

        {notVerified ? (
          <div className="login-notice">
            <p className="login-notice-text">{t('auth.notVerified.body')}</p>
          </div>
        ) : (
          serverError && <p className="login-error">{serverError}</p>
        )}

        <Button type="submit" fullWidth variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
          {isSubmitting ? t('common.loading') : t('auth.loginSubmit')}
        </Button>

        <p className="login-switch">
          {t('auth.dontHaveAccount')} <Link to="/register">{t('auth.registerCta')}</Link>
        </p>
      </motion.form>
    </div>
  )
}

export default Login
