import './JobCreate.scss'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useForm, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Button, ButtonVariant, FieldShell, PostAsSelect, PostAsSurface } from '@shared/components'
import type { ContactEntry } from '@shared/components'
import { useUser } from '@shared/contexts'
import useMyOrganizations from '@shared/hooks/useMyOrganizations'
import usePostAs from '@shared/hooks/usePostAs'
import { createJob, type CreateJobStatus } from '@utils/api/jobs'
import { fetchOrgSettings } from '@utils/api/organizations'
import { fetchUserSettings } from '@utils/api/settings'
import { parseProblem } from '@utils/api/types'
import { canPublishAs } from '@pages/settings/lib/permissions'
import useUnsavedGuard from '@pages/settings/lib/useUnsavedGuard'
import { UnsavedChangesModal } from '@pages/settings/components/UnsavedChangesModal'
import { ApplyFields } from '@pages/jobs/components/ApplyFields'
import { PostingFields } from '@pages/jobs/components/PostingFields'
import DraftSaved from './DraftSaved'
import { buildJobSchema, JOB_DEFAULTS, toCreateJobPayload, type JobFormData } from './schema'

const JobCreate = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useUser()

  const { organizations, loading, refresh } = useMyOrganizations()
  const publishable = useMemo(
    () => organizations.filter((row) => canPublishAs(row.role, PostAsSurface.JOB)),
    [organizations],
  )
  const postAs = usePostAs(publishable)
  const actingOrgSlug =
    publishable.find((row) => row.organization.id === postAs.value)?.organization.handle ?? null

  const [savedContacts, setSavedContacts] = useState<readonly ContactEntry[]>([])
  const [error, setError] = useState<string | null>(null)
  const [draftSlug, setDraftSlug] = useState<string | null>(null)
  const [publishedSlug, setPublishedSlug] = useState<string | null>(null)

  const intent = useRef<CreateJobStatus>('OPEN')

  const publishSchema = useMemo(() => buildJobSchema(t), [t])
  const draftSchema = useMemo(() => buildJobSchema(t, true), [t])

  const resolver = useCallback<Resolver<JobFormData>>(
    (values, context, options) =>
      zodResolver(intent.current === 'DRAFT' ? draftSchema : publishSchema)(values, context, options),
    [draftSchema, publishSchema],
  )

  const {
    register,
    control,
    handleSubmit,
    reset,
    trigger,
    getValues,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<JobFormData>({ resolver, mode: 'onChange', defaultValues: JOB_DEFAULTS })

  useEffect(() => {
    if (!user) return
    let alive = true
    const pending = actingOrgSlug ? fetchOrgSettings(actingOrgSlug) : fetchUserSettings(user.username)
    pending
      .then((dto) => {
        if (alive) setSavedContacts(dto.otherContactInfo)
      })
      .catch(() => {
        if (alive) setSavedContacts([])
      })
    return () => {
      alive = false
    }
  }, [user, actingOrgSlug])

  const persist = async (values: JobFormData): Promise<boolean> => {
    setError(null)
    const status = intent.current
    try {
      const job = await createJob(toCreateJobPayload(values, postAs.createdForId, status))
      reset(values)
      if (status === 'OPEN') setPublishedSlug(job.slug)
      else setDraftSlug(job.slug)
      return true
    } catch (caught) {
      const problem = parseProblem(caught)
      if (problem.status === 403) refresh()
      setError(problem.detail || t('errors.serverError'))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty,
    onSave: async () => {
      intent.current = 'DRAFT'
      return (await trigger()) ? persist(getValues()) : false
    },
    onDiscard: () => reset(JOB_DEFAULTS),
  })

  useEffect(() => {
    if (publishedSlug) navigate(`/jobs/${publishedSlug}`, { replace: true })
  }, [publishedSlug, navigate])

  return (
    <div className="job-create">
      <div className="job-create-card">
        <header className="job-create-head">
          <h1>{t('jobs.create.title')}</h1>
          <p className="job-create-sub">{t('jobs.create.subtitle')}</p>
        </header>

        {draftSlug ? (
          <DraftSaved slug={draftSlug} />
        ) : (
          <form
            className="job-create-form"
            onSubmit={(event) => handleSubmit(persist)(event)}
            noValidate
          >
            <div className="job-create-identity">
              {!loading && publishable.length > 0 && (
                <FieldShell label={t('jobs.create.postingAs')} htmlLabel={false}>
                  <PostAsSelect
                    organizations={publishable}
                    value={postAs.value}
                    onChange={postAs.setValue}
                    disabled={isSubmitting}
                  />
                </FieldShell>
              )}
            </div>

            <PostingFields register={register} control={control} errors={errors} />

            <section className="job-create-apply">
              <ApplyFields
                register={register}
                control={control}
                errors={errors}
                savedContacts={savedContacts}
              />
            </section>

            {error && <p className="job-create-error">{error}</p>}

            <div className="job-create-actions">
              <Button
                variant={ButtonVariant.GHOST}
                onClick={() => navigate('/jobs')}
                disabled={!isDirty || isSubmitting}
              >
                {t('common.cancel')}
              </Button>

              <Button
                type="submit"
                variant={ButtonVariant.SECONDARY}
                onClick={() => {
                  intent.current = 'DRAFT'
                }}
                disabled={isSubmitting}
              >
                {isSubmitting ? t('common.saving') : t('jobs.create.saveDraft')}
              </Button>

              <Button
                type="submit"
                variant={ButtonVariant.PRIMARY}
                onClick={() => {
                  intent.current = 'OPEN'
                }}
                disabled={isSubmitting}
              >
                {isSubmitting ? t('jobs.create.publishing') : t('jobs.create.publish')}
              </Button>
            </div>
          </form>
        )}
      </div>

      <UnsavedChangesModal guard={guard} />
    </div>
  )
}

export default JobCreate
