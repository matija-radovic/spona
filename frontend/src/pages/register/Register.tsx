import './Register.scss'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { AnimatePresence, motion } from 'motion/react'
import { Button, ButtonVariant } from '@shared/components/Button'
import { useUser } from '@shared/contexts'
import {
  checkUsernameAvailable,
  createOrganization,
  createUser,
  type CreateOrganizationPayload,
  type CreateUserPayload,
  type UserDto,
} from '@utils/api/auth'
import {
  buildOrgSchema,
  buildRegisterSchema,
  ORG_DEFAULTS,
  REGISTER_DEFAULTS,
  type OrgData,
  type RegisterData,
} from './schema'
import StageAccount from './stages/StageAccount'
import StagePersonal from './stages/StagePersonal'
import StageOrganization from './stages/StageOrganization'

const Stage = {
  Account: 1,
  Personal: 2,
  Organization: 3,
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
  avatarUrl: data.avatarUrl || null,
  location: orNull(data.location),
  otherContactInfo: data.otherContactInfo,
})

const toOrgPayload = (data: OrgData): CreateOrganizationPayload => ({
  slug: data.slug,
  displayName: data.displayName.trim(),
  legalName: data.legalName.trim(),
  description: orNull(data.description),
  website: orNull(data.website),
  avatarUrl: data.avatarUrl || null,
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
  const { login } = useUser()
  const orgMode = useSearchParams()[0].get('org') === '1'  // Stage.Organization only exists when arriving via the Main.tsx employer button.

  const [stage, setStage] = useState<Stage>(Stage.Account)
  const [usernameTaken, setUsernameTaken] = useState(false)
  const [busy, setBusy] = useState(false)
  const [createdUser, setCreatedUser] = useState<UserDto | null>(null)

  const registerSchema = useMemo(() => buildRegisterSchema(t), [t])
  const orgSchema = useMemo(() => buildOrgSchema(t), [t])

  const userForm = useForm<RegisterData>({
    resolver: zodResolver(registerSchema),
    mode: 'onChange',
    defaultValues: REGISTER_DEFAULTS,
  })
  const orgForm = useForm<OrgData>({
    resolver: zodResolver(orgSchema),
    mode: 'onChange',
    defaultValues: ORG_DEFAULTS,
  })

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

  const finishUser = (user: UserDto) => {
    login({
      id: user.id,
      username: user.username,
      fullName: user.fullName,
      email: user.email,
      avatarUrl: user.avatarUrl ?? '',
    })
  }

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
    try {
      const user = await createUser(toUserPayload(userForm.getValues()))
      setCreatedUser(user)
      if (orgMode) {
        setStage(Stage.Organization)
      } else {
        finishUser(user)
      }
    } finally {
      setBusy(false)
    }
  }

  const handleCreateOrg = async () => {
    const valid = await orgForm.trigger()
    if (!valid || !createdUser) return

    setBusy(true)
    try {
      await createOrganization(toOrgPayload(orgForm.getValues()))
      finishUser(createdUser)
    } finally {
      setBusy(false)
    }
  }

  const handleCreateLater = () => {
    if (createdUser) finishUser(createdUser)
  }

  const steps = orgMode
    ? ['auth.steps.account', 'auth.steps.personal', 'auth.steps.organization']
    : ['auth.steps.account', 'auth.steps.personal']

  return (
    <div className="register">
      <div className="register-card">
        <header className="register-head">
          <h2>{stage === Stage.Organization ? t('auth.org.title') : t('auth.registerTitle')}</h2>
          <p className="register-sub">
            {stage === Stage.Account && t('auth.registerSubtitle')}
            {stage === Stage.Personal && t('auth.personalSubtitle')}
            {stage === Stage.Organization && t('auth.org.subtitle')}
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
            {stage === Stage.Organization && <StageOrganization form={orgForm} />}
          </motion.div>
        </AnimatePresence>

        <div className="register-actions">
          {/* No Back out of Organization: the user account already exists by then. */}
          {stage > Stage.Account && stage < Stage.Organization && (
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
            <Button
              variant={ButtonVariant.PRIMARY}
              onClick={handlePersonalNext}
              disabled={busy}
            >
              {busy ? t('auth.creating') : orgMode ? t('auth.next') : t('common.confirm')}
            </Button>
          )}

          {stage === Stage.Organization && (
            <div className="register-actions-org">
              <Button
                variant={ButtonVariant.GHOST}
                onClick={handleCreateLater}
                disabled={busy}
              >
                {t('auth.org.createLater')}
              </Button>
              <Button
                variant={ButtonVariant.PRIMARY}
                onClick={handleCreateOrg}
                disabled={busy}
              >
                {busy ? t('auth.creating') : t('auth.org.create')}
              </Button>
            </div>
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
