import './Post.scss'
import { useEffect, useState } from 'react'
import { useMatch, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components'
import { useUser } from '@shared/contexts'
import { loadMyOrganizations } from '@shared/hooks/useMyOrganizations'
import { actorPath } from '@utils/actorPath'
import { applyReactionDelta } from '@utils/applyReactionDelta'
import { setReaction } from '@utils/api/reactions'
import { parseProblem, type ProblemDetail } from '@utils/api/types'
import {
  deletePost,
  fetchCommentContext,
  fetchComments,
  fetchPost,
  type CommentContextDto,
  type PostDto,
} from '@utils/api/posts'
import {
  commentsCacheKey,
  COMMENTS_PAGE_SIZE,
  DEFAULT_COMMENT_SORT,
  setCommentsCache,
} from '@pages/posts/lib/commentsCache'
import { CommentThread } from '@pages/posts/components/CommentThread'
import { PostRail } from '@pages/posts/components/PostRail'
import { PostView } from '@pages/posts/components/PostView'

/** The rail drops at an existing breakpoint: `$tablet` in _mediaqueries.scss. */
const RAIL_QUERY = '(min-width: 1025px)'

interface PostBodyProps {
  initialPost: PostDto
  anchor: CommentContextDto | null
  hasRail: boolean
}

const PostBody = ({ initialPost, anchor, hasRail }: PostBodyProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [post, setPost] = useState(initialPost)
  const [deleted, setDeleted] = useState(false)

  const onReact = (emoji: string, next: boolean) => {
    const before = post.reactions
    setPost((current) => ({ ...current, reactions: applyReactionDelta(before, emoji, next) }))
    setReaction('post', post.id, emoji, next).catch(() => {
      setPost((current) => ({ ...current, reactions: before }))
    })
  }

  const onDelete = async () => {
    try {
      await deletePost(post.id)
    } catch {
      return
    }
    setDeleted(true)
    if (window.history.length > 1) navigate(-1)
    else navigate(actorPath(post.author), { replace: true })
  }

  if (deleted) {
    return <p className="post-page-status">{t('post.deleted')}</p>
  }

  return (
    <div className="post-page">
      <div className="post-page-column">
        <PostView
          post={post}
          onReact={onReact}
          onUpdated={(content) => setPost((current) => ({ ...current, content }))}
          onDelete={onDelete}
        />

        <CommentThread
          postId={post.id}
          commentCount={post.commentCount}
          onCommentCountChange={(delta) =>
            setPost((current) => ({
              ...current,
              commentCount: Math.max(0, current.commentCount + delta),
            }))
          }
          anchor={anchor}
        />
      </div>

      {hasRail && <PostRail post={post} />}
    </div>
  )
}

const Post = () => {
  const { t } = useTranslation()
  const { postId } = useParams()
  const { user } = useUser()
  const anchorMatch = useMatch('/posts/:postId/comments/:commentId')
  const commentId = anchorMatch?.params.commentId

  const [post, setPost] = useState<PostDto | null>(null)
  const [anchor, setAnchor] = useState<CommentContextDto | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)

  const [loadedKey, setLoadedKey] = useState<string | undefined>(undefined)
  const [reloadKey, setReloadKey] = useState(0)

  const [hasRail, setHasRail] = useState(() => window.matchMedia(RAIL_QUERY).matches)

  useEffect(() => {
    const mql = window.matchMedia(RAIL_QUERY)
    const onChange = (event: MediaQueryListEvent) => setHasRail(event.matches)
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [])

  const key = `${postId}::${commentId ?? ''}`

  useEffect(() => {
    if (!postId) return
    let alive = true

    Promise.allSettled([
      fetchPost(postId),
      commentId ? fetchCommentContext(commentId) : Promise.resolve(null),
      fetchComments(postId, { page: 1, size: COMMENTS_PAGE_SIZE, exclude: commentId }),
      user ? loadMyOrganizations(user.id, user.username) : Promise.resolve([]),
    ]).then(([postRes, anchorRes, commentsRes]) => {
      if (!alive) return

      if (postRes.status === 'rejected') {
        setPost(null)
        setAnchor(null)
        setProblem(parseProblem(postRes.reason))
        setLoadedKey(key)
        return
      }

      // Invalid anchor, normal page
      setAnchor(anchorRes.status === 'fulfilled' ? anchorRes.value : null)
      setPost(postRes.value)
      setProblem(null)

      if (commentsRes.status === 'fulfilled') {
        setCommentsCache(commentsCacheKey(postId, DEFAULT_COMMENT_SORT), {
          items: commentsRes.value.content,
          page: commentsRes.value.page,
          last: commentsRes.value.last,
          totalElements: commentsRes.value.totalElements,
        })
      }
      setLoadedKey(key)
    })

    return () => {
      alive = false
    }
  }, [postId, commentId, key, user, reloadKey])

  const loading = loadedKey !== key

  if (loading) {
    return <p className="post-page-status">{t('common.loading')}</p>
  }

  if (problem || !post) {
    const isNotFound = problem?.status === 404
    return (
      <div className="post-page-status">
        <p>{isNotFound ? t('post.notFound') : problem?.detail || t('errors.serverError')}</p>
        {!isNotFound && (
          <Button
            variant={ButtonVariant.TERTIARY}
            onClick={() => {
              setLoadedKey(undefined)
              setReloadKey((current) => current + 1)
            }}
          >
            {t('jobs.results.retry')}
          </Button>
        )}
      </div>
    )
  }

  return <PostBody key={postId} initialPost={post} anchor={anchor} hasRail={hasRail} />
}

export default Post
