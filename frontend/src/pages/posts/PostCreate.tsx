import './PostCreate.scss'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Button,
  ButtonVariant,
  FieldShell,
  Input,
  PostAsSelect,
  PostAsSurface,
  UnsavedChangesModal,
} from '@shared/components'
import useMyOrganizations from '@shared/hooks/useMyOrganizations'
import usePostAs from '@shared/hooks/usePostAs'
import useUnsavedGuard from '@shared/hooks/useUnsavedGuard'
import { createPost } from '@utils/api/posts'
import { parseProblem } from '@utils/api/types'
import { problemMessage } from '@utils/api/problemMessage'
import { canPublishAs } from '@utils/postAs'
import { POST_MAX } from '@utils/constants/validation'
import {
  buildPostSchema,
  POST_DEFAULTS,
  toCreatePostPayload,
  type PostFormData,
} from '@pages/posts/postSchema'

const PostCreate = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const { organizations, loading, refresh } = useMyOrganizations()
  const publishable = useMemo(
    () => organizations.filter((row) => canPublishAs(row.role, PostAsSurface.CONTENT)),
    [organizations],
  )
  const postAs = usePostAs(publishable, params.get('as'))

  const [error, setError] = useState<string | null>(null)
  const [destination, setDestination] = useState<string | null>(null)

  const schema = useMemo(() => buildPostSchema(t), [t])

  const {
    register,
    handleSubmit,
    reset,
    trigger,
    getValues,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<PostFormData>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: POST_DEFAULTS,
  })

  const persist = async (values: PostFormData): Promise<boolean> => {
    setError(null)
    try {
      const post = await createPost(toCreatePostPayload(values, postAs.createdForId))
      // Clears `isDirty`, so the guard does not block the page's own success navigation.
      reset(values)
      setDestination(`/posts/${post.id}`)
      return true
    } catch (caught) {
      const problem = parseProblem(caught)
      if (problem.status === 403) refresh()
      setError(problemMessage(problem, t))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty,
    onSave: async () => ((await trigger()) ? persist(getValues()) : false),
  })

  useEffect(() => {
    if (destination) navigate(destination, { replace: true })
  }, [destination, navigate])

  return (
    <div className="post-create">
      <div className="post-create-card">
        <header className="post-create-head">
          <h1>{t('post.create.title')}</h1>
          <p className="post-create-sub">{t('post.create.subtitle')}</p>
        </header>

        <form
          className="post-create-form"
          onSubmit={(event) => handleSubmit(persist)(event)}
          noValidate
        >
          <div className="post-create-identity">
            {!loading && publishable.length > 0 && (
              <FieldShell label={t('post.create.postingAs')} htmlLabel={false}>
                <PostAsSelect
                  organizations={publishable}
                  value={postAs.value}
                  onChange={postAs.setValue}
                  disabled={isSubmitting}
                />
              </FieldShell>
            )}
          </div>

          <FieldShell
            label={t('post.create.content')}
            hint={t('post.create.lengthHint', { max: POST_MAX })}
            required
          >
            <Input
              multiline
              rows={10}
              placeholder={t('post.create.placeholder')}
              error={errors.content?.message}
              {...register('content')}
            />
          </FieldShell>

          {error && <p className="post-create-error">{error}</p>}

          <div className="post-create-actions">
            <Button
              variant={ButtonVariant.GHOST}
              onClick={() => navigate('/feed')}
              disabled={isSubmitting}
            >
              {t('common.cancel')}
            </Button>

            <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
              {isSubmitting ? t('post.create.publishing') : t('post.create.publish')}
            </Button>
          </div>
        </form>
      </div>

      <UnsavedChangesModal guard={guard} saveLabel={t('post.create.publish')} />
    </div>
  )
}

export default PostCreate
