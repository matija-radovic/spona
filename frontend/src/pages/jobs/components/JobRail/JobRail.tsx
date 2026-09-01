import './JobRail.scss'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ActorAvatar, ActorAvatarSize, FollowButton } from '@shared/components'
import { actorPath } from '@utils/actorPath'
import { truncateText } from '@utils/truncateText'
import type { ActorRefDto } from '@utils/api/profiles'

const BIO_MAX_CHARS = 160

interface JobRailProps {
  org: ActorRefDto & { actorType: 'org' }
  bio?: string | null
  followedByViewer?: boolean
}

const JobRail = ({ org, bio, followedByViewer }: JobRailProps) => {
  const [following, setFollowing] = useState(followedByViewer ?? false)

  const snippet = bio ? truncateText(bio, BIO_MAX_CHARS).text : null

  return (
    <aside className="job-rail">
      <div className="job-rail-sticky">
        <section className="job-rail-card">
          <Link to={actorPath(org)} className="job-rail-identity">
            <ActorAvatar
              avatarUrl={org.avatarUrl}
              actorType="org"
              size={ActorAvatarSize.MD}
              alt={org.name}
            />
            <span className="job-rail-name">{org.name}</span>
            <span className="job-rail-handle">@{org.handle}</span>
          </Link>

          {snippet && <p className="job-rail-bio">{snippet}</p>}

          <FollowButton
            actorId={org.id}
            following={following}
            onChange={setFollowing}
            className="job-rail-follow"
          />
        </section>
      </div>
    </aside>
  )
}

export default JobRail
