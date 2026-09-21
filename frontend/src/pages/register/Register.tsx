import './Register.scss'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { AnimatePresence, motion } from 'motion/react'
import { IconMail } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import {
  checkUsernameAvailable,
  registerUser,
  resendVerification,
  type CreateUserPayload,
} from '@utils/api/auth'
import { parseProblem } from '@utils/api/types'
import { rememberAfterAuth } from '@utils/afterAuth'
import { buildRegisterSchema, REGISTER_DEFAULTS, type RegisterData } from './schema'
import StageAccount from './stages/StageAccount'
import StagePersonal from './stages/StagePersonal'

const Stage = {
  Account: 1,
  Personal: 2,
} as const
type Stage = (typeof Stage)[keyof typeof Stage]

/** Empty optional strings become null to match the nullable DTO columns. */
const orNull = (value: string): string | null => (value.trim() === '' ? null : value.trim())

const toUserPayload = (data: RegisterData): CreateUserPayload => ({
  username: data.username,
  email: data.email,
  password: data.password,
  fullName: data.fullName.trim(),
  bio: orNull(data.bio),
  avatar: data.avatar || null,
  location: orNull(data.location),
  otherContactInfo: data.otherContactInfo,
})

const slide = {
  initial: { opacity: 0, x: 28 },
  animate: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: -28 },
  transition: { duration: 0.28, ease: 'easeOut' },
} as const


const Register = () => {
  const { t } = useTranslation()
  const orgMode = useSearchParams()[0].get('org') === '1'

  const [stage, setStage] = useState<Stage>(Stage.Account)
  const [usernameTaken, setUsernameTaken] = useState(false)
  const [busy, setBusy] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [resend, setResend] = useState<'idle' | 'sending' | 'sent'>('idle')

  const registerSchema = useMemo(() => buildRegisterSchema(t), [t])

  const userForm = useForm<RegisterData>({
    resolver: zodResolver(registerSchema),
    mode: 'onChange',
    defaultValues: REGISTER_DEFAULTS,
  })

  useEffect(() => {
    if (orgMode) rememberAfterAuth('/companies/new')
  }, [orgMode])

  // Re-typing the username clears the "taken" state; editing the password
  // re-validates confirmPassword so its green check flips the moment they diverge.
  useEffect(() => {
    return userForm.subscribe({
      formState: { values: true },
      callback: ({ name }) => {
        if (name === 'username') setUsernameTaken(false)
        if (name === 'password' && userForm.getValues('confirmPassword')) {
          userForm.trigger('confirmPassword')
        }
      },
    })
  }, [userForm])

  const handleAccountNext = async () => {
    const valid = await userForm.trigger(['username', 'email', 'password', 'confirmPassword'])
    if (!valid) return

    setBusy(true)
    try {
      const available = await checkUsernameAvailable(userForm.getValues('username'))
      if (!available) {
        setUsernameTaken(true)
        return
      }
      setStage(Stage.Personal)
    } finally {
      setBusy(false)
    }
  }

  // Every personal-stage field is optional, so there is nothing to validate first.
  const handlePersonalNext = async () => {
    setBusy(true)
    setSubmitError(null)
    try {
      const values = userForm.getValues()
      await registerUser(toUserPayload(values))
      setSentTo(values.email)
    } catch (caught) {
      // A taken email or a rejected field; which one is not revealed
      const { status } = parseProblem(caught)
      setSubmitError(t(status === 409 || status === 422 ? 'errors.invalidInput' : 'errors.serverError'))
    } finally {
      setBusy(false)
    }
  }

  const onResend = async () => {
    if (!sentTo) return
    setResend('sending')
    try {
      await resendVerification(sentTo)
    } finally {
      setResend('sent')
    }
  }

  const steps = ['auth.steps.account', 'auth.steps.personal']

  if (sentTo) {
    return (
      <div className="register">
        <div className="register-card register-done">
          <div className="register-done-icon">
            <IconMail aria-hidden />
          </div>
          <h2>{t('auth.registerDone.title')}</h2>
          <p className="register-done-body">{t('auth.registerDone.body', { email: sentTo })}</p>
          <Button to="/login" variant={ButtonVariant.PRIMARY} fullWidth>
            {t('auth.registerDone.toLogin')}
          </Button>
          <div className="register-done-resend">
            <span>{t('auth.registerDone.noEmail')}</span>
            <button
              type="button"
              className="register-done-resend-btn"
              onClick={onResend}
              disabled={resend !== 'idle'}
            >
              {resend === 'sending' ? t('auth.notVerified.sending') : t('auth.registerDone.resend')}
            </button>
          </div>
          {resend === 'sent' && (
            <p className="register-done-sent">{t('auth.notVerified.sent')}</p>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="register">
      <div className="register-card">
        <header className="register-head">
          <h2>{t('auth.registerTitle')}</h2>
          <p className="register-sub">
            {stage === Stage.Account && t('auth.registerSubtitle')}
            {stage === Stage.Personal && t('auth.personalSubtitle')}
          </p>
        </header>

        <ol className="register-steps">
          {steps.map((key, index) => {
            const stepNumber = index + 1
            const state =
              stepNumber === stage ? 'is-active' : stepNumber < stage ? 'is-done' : ''
            return (
              <li key={key} className={`register-step ${state}`}>
                <span className="register-step-num">{stepNumber}</span>
                <span className="register-step-label">{t(key)}</span>
              </li>
            )
          })}
        </ol>

        <AnimatePresence mode="wait">
          <motion.div key={stage} {...slide}>
            {stage === Stage.Account && <StageAccount form={userForm} usernameTaken={usernameTaken} />}
            {stage === Stage.Personal && <StagePersonal form={userForm} />}
          </motion.div>
        </AnimatePresence>

        {submitError && <p className="register-error">{submitError}</p>}

        <div className="register-actions">
          {stage > Stage.Account && (
            <Button
              variant={ButtonVariant.GHOST}
              onClick={() => setStage(Stage.Account)}
              disabled={busy}
            >
              {t('auth.back')}
            </Button>
          )}

          {stage === Stage.Account && (
            <Button
              className="register-actions-end"
              variant={ButtonVariant.PRIMARY}
              onClick={handleAccountNext}
              disabled={busy}
            >
              {busy ? t('auth.checking') : t('auth.next')}
            </Button>
          )}

          {stage === Stage.Personal && (
            <Button variant={ButtonVariant.PRIMARY} onClick={handlePersonalNext} disabled={busy}>
              {busy ? t('auth.creating') : t('common.confirm')}
            </Button>
          )}
        </div>

        {stage === Stage.Account && (
          <p className="register-switch">
            {t('auth.alreadyHaveAccount')} <Link to="/login">{t('auth.signInCta')}</Link>
          </p>
        )}
      </div>
    </div>
  )
}

export default Register
