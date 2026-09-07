import './ProfileHeader.scss'
import { useState } from 'react'
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
import { canPublishAs } from '@utils/postAs'
import { safeHref, displayUrl, WEB_PROTOCOLS } from '@utils/safeUrl'
import type { ProfileDto } from '@utils/api/profiles'

interface ProfileHeaderProps {
  profile: ProfileDto
}

const ProfileHeader = ({ profile }: ProfileHeaderProps) => {
  const { t } = useTranslation()
  const { organizations } = useMyOrganizations()

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
  const website = isUser ? null : safeHref(profile.website, { protocols: WEB_PROTOCOLS })
  const settingsPath = isUser ? `/users/${handle}/settings` : `/companies/${handle}/settings`
  const profileUrl = `${window.location.origin}${actorPath({ actorType: profile.actorType, handle })}`

  const subText = [isUser ? `@${handle}` : null, profile.location].filter(Boolean).join(' • ')

  // On a user profile "may I publish as this actor" is "is this me"; on an organization
  // it is the viewer's role, run through the matrix the destination's picker also runs.
  const role = isUser
    ? undefined
    : organizations.find((row) => row.organization.id === profile.id)?.role
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
          <span>{t('profile.followersCount', { count: followers })}</span>
          {isUser && (
            <>
              <span className="profile-head-dot" aria-hidden>•</span>
              <span>{t('profile.followingCount', { count: profile.following })}</span>
            </>
          )}
        </div>

        {bio && <p className="profile-head-bio">{bio}</p>}
      </div>

      {/* Two groups of same-sized buttons: what you can do *with* this profile
          (settings on your own, follow on someone else's, plus the copy link),
          then the creation actions, gated one per surface. The split is spacing,
          not a rule (see the SCSS); visitors get a single group. */}
      <div className="profile-head-actions">
        <div className="profile-head-action-group">
          {profile.viewerCanEdit ? (
            <Button variant={ButtonVariant.TERTIARY} to={settingsPath}>
              <IconSettings aria-hidden />
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
            {copied ? <IconCheck aria-hidden /> : <IconLink aria-hidden />}
            {copied ? t('common.linkCopied') : t('common.copyLink')}
          </Button>
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
