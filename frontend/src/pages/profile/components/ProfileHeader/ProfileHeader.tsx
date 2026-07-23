import './ProfileHeader.scss'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconSettings, IconPlus, IconExternalLink, IconLink, IconCheck } from '@tabler/icons-react'
import { ActorAvatar, ActorAvatarSize, Button, ButtonVariant, FollowButton } from '@shared/components'
import useCopyLink from '@shared/hooks/useCopyLink'
import { actorPath } from '@utils/actorPath'
import type { ProfileDto } from '@utils/api/profiles'

interface ProfileHeaderProps {
  profile: ProfileDto
}

/**
 * The profile page-content header: avatar cell, identity cell (name / handle •
 * location • site / follows / bio), and an actions cell that switches on
 * `viewerCanEdit`. Follow is optimistic (via FollowButton) and syncs the
 * follower count shown in the identity cell.
 */
const ProfileHeader = ({ profile }: ProfileHeaderProps) => {
  const { t } = useTranslation()

  const [following, setFollowing] = useState(profile.isFollowedByViewer ?? false)
  const [followers, setFollowers] = useState(profile.followers)

  const onFollowChange = (next: boolean) => {
    setFollowing(next)
    setFollowers((count) => count + (next ? 1 : -1))
  }

  const isUser = profile.actorType === 'user'
  const name = isUser ? profile.fullName : profile.displayName
  const handle = isUser ? profile.username : profile.slug
  const bio = isUser ? profile.bio : profile.description
  const website = isUser ? null : profile.website
  const settingsPath = isUser ? `/users/${handle}/settings` : `/companies/${handle}/settings`
  const profileUrl = `${window.location.origin}${actorPath({ actorType: profile.actorType, handle })}`

  const subText = [isUser ? `@${handle}` : null, profile.location].filter(Boolean).join(' • ')

  const { copied, copy } = useCopyLink(profileUrl)

  return (
    <header className="profile-head">
      <div className="profile-head-avatar">
        <ActorAvatar
          avatarUrl={profile.avatarUrl}
          actorType={profile.actorType}
          size={ActorAvatarSize.XL}
          alt={name}
        />
      </div>

      <div className="profile-head-identity">
        <h1 className="profile-head-name">{name}</h1>

        {(subText || website) && (
          <div className="profile-head-sub">
            {subText && <span>{subText}</span>}
            {website && (
              <>
                {subText && <span className="profile-head-dot" aria-hidden>•</span>}
                <a
                  className="profile-head-site"
                  href={website}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <IconExternalLink size={14} aria-hidden />
                  {website.replace(/^https?:\/\//, '')}
                </a>
              </>
            )}
          </div>
        )}

        <div className="profile-head-follows">
          <span>{t('profile.followersCount', { count: followers })}</span>
          <span className="profile-head-dot" aria-hidden>•</span>
          <span>{t('profile.followingCount', { count: profile.following })}</span>
        </div>

        {bio && <p className="profile-head-bio">{bio}</p>}
      </div>

      {/* Two groups of same-sized buttons: what you can do *with* this profile
          (settings on your own, follow on someone else's, plus the copy link),
          then - only when you can edit it - the creation actions. The split is
          spacing, not a rule (see the SCSS); visitors get a single group. */}
      <div className="profile-head-actions">
        <div className="profile-head-action-group">
          {profile.viewerCanEdit ? (
            <Button variant={ButtonVariant.TERTIARY} to={settingsPath}>
              <IconSettings size={17} aria-hidden />
              {t('profile.settings')}
            </Button>
          ) : (
            <FollowButton actorId={profile.id} following={following} onChange={onFollowChange} />
          )}

          <Button
            variant={ButtonVariant.TERTIARY}
            className={copied ? 'profile-head-copy is-copied' : 'profile-head-copy'}
            onClick={copy}
          >
            {copied ? <IconCheck size={17} aria-hidden /> : <IconLink size={17} aria-hidden />}
            {copied ? t('common.linkCopied') : t('common.copyLink')}
          </Button>
        </div>

        {profile.viewerCanEdit && (
          <div className="profile-head-action-group">
            <Button variant={ButtonVariant.PRIMARY} to="/jobs/new">
              <IconPlus size={17} aria-hidden />
              {t('profile.createJob')}
            </Button>
          </div>
        )}
      </div>
    </header>
  )
}

export default ProfileHeader
