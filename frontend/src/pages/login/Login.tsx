import './Login.scss'
import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { motion } from 'motion/react'
import { TextField } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/Button'
import { useUser } from '@shared/contexts'
import { loginRequest, resendVerification } from '@utils/api/auth'
import { parseProblem } from '@utils/api/types'

const buildSchema = (t: (key: string) => string) =>
  z.object({
    identifier: z.string().min(1, t('errors.required')),
    password: z.string().min(1, t('errors.required')),
  })

type LoginData = z.infer<ReturnType<typeof buildSchema>>

type ResendState = 'idle' | 'sending' | 'sent'

const Login = () => {
  const { t } = useTranslation()
  const { login } = useUser()
  const [serverError, setServerError] = useState<string | null>(null)
  const [notVerified, setNotVerified] = useState(false)
  const [resend, setResend] = useState<ResendState>('idle')

  const schema = useMemo(() => buildSchema(t), [t])
  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors, dirtyFields, isSubmitting },
  } = useForm<LoginData>({ resolver: zodResolver(schema), mode: 'onChange' })

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    setNotVerified(false)
    setResend('idle')
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

  const onResend = async () => {
    setResend('sending')
    try {
      await resendVerification(getValues('identifier'))
    } finally {
      setResend('sent')
    }
  }

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

        <TextField
          label={t('auth.usernameOrEmail')}
          required
          autoComplete="username"
          error={errors.identifier?.message}
          valid={!!dirtyFields.identifier && !errors.identifier}
          {...register('identifier')}
        />

        <TextField
          label={t('auth.password')}
          required
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          valid={!!dirtyFields.password && !errors.password}
          {...register('password')}
        />

        <div className="login-forgot-row">
          <Link to="/reset" className="login-forgot">
            {t('auth.forgotPassword')}
          </Link>
        </div>

        {notVerified ? (
          <div className="login-notice">
            <p className="login-notice-text">{t('auth.notVerified.body')}</p>
            <Button
              type="button"
              variant={ButtonVariant.SECONDARY}
              onClick={onResend}
              disabled={resend === 'sending'}
            >
              {resend === 'sending' ? t('auth.notVerified.sending') : t('auth.notVerified.resend')}
            </Button>
            {resend === 'sent' && <p className="login-notice-sent">{t('auth.notVerified.sent')}</p>}
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
