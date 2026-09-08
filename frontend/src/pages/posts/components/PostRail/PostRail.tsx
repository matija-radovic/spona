import './PostRail.scss'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ActorAvatar, ActorAvatarSize, FollowButton, RailLegal } from '@shared/components'
import { useUser } from '@shared/contexts'
import { actorPath } from '@utils/actorPath'
import { truncateText } from '@utils/truncateText'
import type { PostDto } from '@utils/api/posts'

/** Enough of a bio to place someone, not enough to become a second profile. */
const BIO_MAX_CHARS = 160

interface PostRailProps {
  post: PostDto
}

const PostRail = ({ post }: PostRailProps) => {
  const { user } = useUser()
  const { author } = post
  const [following, setFollowing] = useState(post.authorFollowedByViewer ?? false)

  const bio = post.authorBio ? truncateText(post.authorBio, BIO_MAX_CHARS).text : null
  const isOwnAuthor = author.actorType === 'user' && author.id === user?.id

  return (
    <aside className="post-rail">
      <div className="post-rail-sticky">
        <section className="post-rail-card post-rail-author">
          <Link to={actorPath(author)} className="post-rail-identity">
            <ActorAvatar
              avatarUrl={author.avatarUrl}
              actorType={author.actorType}
              size={ActorAvatarSize.MD}
              alt={author.name}
            />
            <span className="post-rail-name">{author.name}</span>
            <span className="post-rail-handle">@{author.handle}</span>
          </Link>

          {bio && <p className="post-rail-bio">{bio}</p>}

          {!isOwnAuthor && (
            <FollowButton
              actorId={author.id}
              following={following}
              onChange={setFollowing}
              className="post-rail-follow"
            />
          )}
        </section>

        <RailLegal />
      </div>
    </aside>
  )
}

export default PostRail
