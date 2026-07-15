import './FollowButton.scss'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconUserPlus, IconUserCheck } from '@tabler/icons-react'
import { useUser } from '@shared/contexts'
import { setFollow } from '@utils/api/profiles'
import { Button, ButtonVariant } from '../Button'

interface FollowButtonProps {
  actorId: string
  /** Current follow state - the parent owns it (and any follower count). */
  following: boolean
  /** Called with the next state on optimistic flip, and reverted on rejection. */
  onChange: (next: boolean) => void
  className?: string
}

/**
 * Optimistic follow / unfollow. Flips the parent state immediately, fires
 * `setFollow`, and reverts on rejection (the JobDetail heart pattern, with the
 * same busy guard). Guests see the button but clicking sends them to /login.
 *
 * Renders through `Button` so it lines up with every other control it sits
 * beside (profile head actions, search rows): primary while not following,
 * secondary once you do - see the SCSS for that state's accent hover.
 */
const FollowButton = ({ actorId, following, onChange, className }: FollowButtonProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isAuthenticated } = useUser()
  const [busy, setBusy] = useState(false)

  const onClick = async () => {
    if (!isAuthenticated) {
      navigate('/login')
      return
    }
    if (busy) return
    const next = !following
    onChange(next) // optimistic
    setBusy(true)
    try {
      await setFollow(actorId, next)
    } catch {
      onChange(!next) // revert
    } finally {
      setBusy(false)
    }
  }

  const rootClass = ['follow-btn', following ? 'is-following' : '', className ?? '']
    .filter(Boolean)
    .join(' ')

  const label = following ? t('profile.following') : t('profile.follow')

  return (
    <Button
      variant={following ? ButtonVariant.SECONDARY : ButtonVariant.PRIMARY}
      className={rootClass}
      onClick={onClick}
      aria-pressed={following}
      aria-label={label}
    >
      {following ? <IconUserCheck size={17} aria-hidden /> : <IconUserPlus size={17} aria-hidden />}
      {/* Wrapped so a caller can hide the text (icon-only on mobile) while the
          aria-label keeps the button named. */}
      <span className="follow-btn-label">{label}</span>
    </Button>
  )
}

export default FollowButton
