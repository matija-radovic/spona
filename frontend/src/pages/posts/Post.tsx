import './Post.scss'
import { useEffect, useState } from 'react'
import { useMatch, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components'
import { useUser } from '@shared/contexts'
import { MQ } from '@shared/css/mediaqueries'
import useMediaQuery from '@shared/hooks/useMediaQuery'
import { loadMyOrganizations } from '@shared/hooks/useMyOrganizations'
import { actorPath } from '@utils/actorPath'
import { applyReactionDelta } from '@utils/applyReactionDelta'
import { cachePost, forgetPost, getCachedPost } from '@utils/postCache'
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

  // The revalidated row replaces the cached one, adjusted during render rather
  // than in an effect: this eslint config errors on `setState` in an effect body.
  const [synced, setSynced] = useState(initialPost)
  if (synced !== initialPost) {
    setSynced(initialPost)
    setPost(initialPost)
  }

  /** Every write goes through here, so the cache and the screen never disagree. */
  const writePost = (next: PostDto) => {
    cachePost(next)
    setPost(next)
  }

  const onReact = (emoji: string, next: boolean) => {
    const before = post.reactions
    writePost({ ...post, reactions: applyReactionDelta(before, emoji, next) })
    setReaction('post', post.id, emoji, next).catch(() => {
      writePost({ ...post, reactions: before })
    })
  }

  const onDelete = async () => {
    try {
      await deletePost(post.id)
    } catch {
      return
    }
    forgetPost(post.id)
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
          onUpdated={(content) => writePost({ ...post, content })}
          onDelete={onDelete}
        />

        <CommentThread
          postId={post.id}
          commentCount={post.commentCount}
          onCommentCountChange={(delta) =>
            writePost({ ...post, commentCount: Math.max(0, post.commentCount + delta) })
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

  const hasRail = useMediaQuery(MQ.exceptTablet)

  const key = `${postId}::${commentId ?? ''}`

  useEffect(() => {
    if (!postId) return
    let alive = true

    // `.dl-main` is the only scroller and nothing else resets it on navigation,
    // so a scrolled list would otherwise open a post already scrolled down. A
    // deep link's own `scrollIntoView` owns the position and must not be fought.
    if (!commentId) {
      document.querySelector<HTMLElement>('.scroll-container')?.scrollTo({ top: 0 })
    }

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
      cachePost(postRes.value)
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

  // A comment permalink is an entry from outside the app, so there is nothing
  // warm behind it. Deriving from `loadedKey` rather than setting state during
  // render is what keeps a stale post or a stale problem from a previous postId
  // from showing under the new one.
  const cached = !commentId && postId ? getCachedPost(postId) : undefined

  const fresh = loadedKey === key ? post : null
  const shown = fresh ?? cached ?? null
  const failed = loadedKey === key ? problem : null
  const loading = !shown && loadedKey !== key

  if (loading) {
    return <p className="post-page-status">{t('common.loading')}</p>
  }

  if (failed || !shown) {
    const isNotFound = failed?.status === 404
    return (
      <div className="post-page-status">
        <p>{isNotFound ? t('post.notFound') : failed?.detail || t('errors.serverError')}</p>
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

  return <PostBody key={postId} initialPost={shown} anchor={anchor} hasRail={hasRail} />
}

export default Post
