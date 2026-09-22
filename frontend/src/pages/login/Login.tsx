import './Login.scss'
import { useEffect, useMemo, useState } from 'react'
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
import type { AuthResponse } from '@utils/api/auth'
import { parseProblem } from '@utils/api/types'

const UNVERIFIED = 'auth.emailNotVerified'
const RESEND_COOLDOWN = 5

type ResendState = 'idle' | 'sending' | 'sent' | 'failed'

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

  const [unverified, setUnverified] = useState<LoginData | null>(null)
  const [resend, setResend] = useState<ResendState>('idle')
  const [cooldown, setCooldown] = useState(0)

  const schema = useMemo(() => buildSchema(t), [t])
  const {
    register,
    handleSubmit,
    formState: { errors, dirtyFields, isSubmitting },
  } = useForm<LoginData>({ resolver: zodResolver(schema), mode: 'onChange' })

  useEffect(() => {
    if (cooldown <= 0) return
    const id = window.setTimeout(() => setCooldown((left) => left - 1), 1000)
    return () => window.clearTimeout(id)
  }, [cooldown])

  const enter = ({ token, user }: AuthResponse) =>
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

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null)
    setUnverified(null)
    setResend('idle')
    try {
      enter(await loginRequest(values))
    } catch (caught) {
      if (parseProblem(caught).code === UNVERIFIED) {
        setUnverified(values)
        setCooldown(RESEND_COOLDOWN)
      } else {
        setServerError(t('errors.loginFailed'))
      }
    }
  })

  const onResend = async () => {
    if (!unverified || cooldown > 0 || resend === 'sending') return
    setResend('sending')
    try {
      enter(await loginRequest(unverified))
      return // verified in another tab between the two attempts, so this signs in
    } catch (caught) {
      setResend(parseProblem(caught).code === UNVERIFIED ? 'sent' : 'failed')
      setCooldown(RESEND_COOLDOWN)
    }
  }

  const resendLabel = () => {
    if (cooldown > 0) return t('auth.notVerified.resendIn', { seconds: cooldown })
    if (resend === 'sending') return t('auth.notVerified.sending')
    return t('auth.notVerified.resend')
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

        {unverified ? (
          <div className="login-notice">
            <p className="login-notice-text">{t('auth.notVerified.body')}</p>
            <div className="login-notice-resend">
              <span>{t('auth.notVerified.noEmail')}</span>
              <button
                type="button"
                className="login-notice-btn"
                onClick={onResend}
                disabled={cooldown > 0 || resend === 'sending'}
              >
                {resendLabel()}
              </button>
            </div>
            <p className="login-notice-status" role="status" aria-live="polite">
              {resend === 'sent' && (
                <span className="login-notice-sent">{t('auth.notVerified.sent')}</span>
              )}
              {resend === 'failed' && (
                <span className="login-notice-failed">{t('auth.notVerified.failed')}</span>
              )}
            </p>
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
