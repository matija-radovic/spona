import './ProfileHeader.scss'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconSettings, IconPlus, IconExternalLink, IconLink, IconCheck } from '@tabler/icons-react'
import {
  ActorAvatar,
  ActorAvatarSize,
  Button,
  ButtonVariant,
  FollowButton,
  PostAsSurface,
} from '@shared/components'
import useCopyLink from '@shared/hooks/useCopyLink'
import useMyOrganizations from '@shared/hooks/useMyOrganizations'
import { actorPath } from '@utils/actorPath'
import { actorDisplayName } from '@utils/actorDisplayName'
import { canPublishAs } from '@utils/postAs'
import { safeHref, displayUrl, WEB_PROTOCOLS } from '@utils/safeUrl'
import type { ProfileDto } from '@utils/api/profiles'

interface ProfileHeaderProps {
  profile: ProfileDto
  followers: number
  following: number | undefined
  isFollowing: boolean
  onFollowChange: (next: boolean) => void
}

const ProfileHeader = ({
  profile,
  followers,
  following,
  isFollowing,
  onFollowChange,
}: ProfileHeaderProps) => {
  const { t } = useTranslation()
  const { organizations } = useMyOrganizations()

  const isUser = profile.actorType === 'user'
  const handle = isUser ? profile.username : profile.slug
  const name = actorDisplayName(isUser ? profile.fullName : profile.displayName, handle)
  const bio = isUser ? profile.bio : profile.description
  const website = isUser ? null : safeHref(profile.website, { protocols: WEB_PROTOCOLS })
  const settingsPath = isUser ? `/users/${handle}/settings` : `/companies/${handle}/settings`
  const path = actorPath({ actorType: profile.actorType, handle })
  const profileUrl = `${window.location.origin}${path}`

  const subText = [isUser ? `@${handle}` : null, profile.location].filter(Boolean).join(' • ')

  const role = isUser
    ? undefined
    : organizations.find((row) => row.organization.id === profile.id)?.role
  const canOpenSettings = !!profile.viewerCanEdit || !!role
  const isOwnProfile = isUser && !!profile.viewerCanEdit
  const canPostContent = isUser
    ? !!profile.viewerCanEdit
    : !!role && canPublishAs(role, PostAsSurface.CONTENT)
  const canPostJob = isUser
    ? !!profile.viewerCanEdit
    : !!role && canPublishAs(role, PostAsSurface.JOB)

  const actingAs = isUser ? '' : `?as=${profile.id}`

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
                  <IconExternalLink aria-hidden />
                  {displayUrl(website)}
                </a>
              </>
            )}
          </div>
        )}

        <div className="profile-head-follows">
          <Link className="profile-head-follow-link" to={`${path}/followers`}>
            {t('profile.followersCount', { count: followers })}
          </Link>
          {isUser && following !== undefined && (
            <>
              <span className="profile-head-dot" aria-hidden>•</span>
              <Link className="profile-head-follow-link" to={`${path}/following`}>
                {t('profile.followingCount', { count: following })}
              </Link>
            </>
          )}
        </div>

        {bio && <p className="profile-head-bio">{bio}</p>}
      </div>

      <div className="profile-head-actions">
        <div className="profile-head-action-group">
          {!isOwnProfile && (
            <FollowButton
              actorId={profile.id}
              following={isFollowing}
              onChange={onFollowChange}
            />
          )}

          <Button
            variant={ButtonVariant.TERTIARY}
            className={copied ? 'profile-head-copy is-copied' : 'profile-head-copy'}
            onClick={copy}
          >
            {copied ? <IconCheck aria-hidden /> : <IconLink aria-hidden />}
            {copied ? t('common.linkCopied') : t('common.copyLink')}
          </Button>

          {canOpenSettings && (
            <Button variant={ButtonVariant.TERTIARY} to={settingsPath}>
              <IconSettings aria-hidden />
              {t('profile.settings')}
            </Button>
          )}
        </div>

        {(canPostContent || canPostJob) && (
          <div className="profile-head-action-group">
            {canPostJob && (
              <Button variant={ButtonVariant.PRIMARY} to={`/jobs/new${actingAs}`}>
                <IconPlus aria-hidden />
                {t('profile.createJob')}
              </Button>
            )}

            {canPostContent && (
              <Button variant={ButtonVariant.PRIMARY} to={`/posts/new${actingAs}`}>
                <IconPlus aria-hidden />
                {t('profile.createPost')}
              </Button>
            )}
          </div>
        )}
      </div>
    </header>
  )
}

export default ProfileHeader
