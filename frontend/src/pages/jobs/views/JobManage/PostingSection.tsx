import { useCallback, useMemo, useRef, useState } from 'react'
import { useForm, type Resolver } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import {
  Button,
  ButtonVariant,
  SaveBar,
  SectionPanel,
  UnsavedChangesModal,
} from '@shared/components'
import useUnsavedGuard from '@shared/hooks/useUnsavedGuard'
import { setJobStatus, updateJob } from '@utils/api/jobs'
import { parseProblem } from '@utils/api/types'
import { PostingFields } from '@pages/jobs/components/PostingFields'
import { DeleteJobDialog } from '@pages/jobs/components/DeleteJobDialog'
import {
  buildJobSchema,
  toJobFormData,
  toUpdateJobPayload,
  type JobFormData,
} from '@pages/jobs/lib/jobSchema'
import { useJobManageContext } from './jobManageContext'

type Intent = 'SAVE' | 'PUBLISH'

const PostingSection = () => {
  const { t } = useTranslation()
  const { job, refetch } = useJobManageContext()

  const isDraft = job.status === 'DRAFT'
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const intent = useRef<Intent>('SAVE')

  const publishSchema = useMemo(() => buildJobSchema(t), [t])
  const draftSchema = useMemo(() => buildJobSchema(t, true), [t])
  const defaults = useMemo(() => toJobFormData(job), [job])

  const resolver = useCallback<Resolver<JobFormData>>(
    (values, context, options) =>
      zodResolver(isDraft && intent.current === 'SAVE' ? draftSchema : publishSchema)(
        values,
        context,
        options,
      ),
    [draftSchema, publishSchema, isDraft],
  )

  const {
    register,
    control,
    handleSubmit,
    reset,
    trigger,
    getValues,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<JobFormData>({ resolver, mode: 'onChange', defaultValues: defaults })

  const persist = async (values: JobFormData): Promise<boolean> => {
    setError(null)
    const publishing = isDraft && intent.current === 'PUBLISH'
    try {
      let saved = await updateJob(job.id, toUpdateJobPayload(values))
      // Publishing is a second write on purpose: the lifecycle has one route, so
      // an ordinary update never carries a status.
      if (publishing) saved = await setJobStatus(job.id, 'OPEN')
      reset(toJobFormData(saved))
      await refetch()
      return true
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty,
    onSave: async () => {
      intent.current = 'SAVE'
      return (await trigger()) ? persist(getValues()) : false
    },
    onDiscard: () => reset(defaults),
  })

  return (
    <SectionPanel
      title={t('jobs.manage.posting.title')}
      className="job-manage-posting"
    >
      <form
        className="job-manage-form"
        onSubmit={(event) => handleSubmit(persist)(event)}
        noValidate
      >
        <PostingFields register={register} control={control} errors={errors} />

        <SaveBar error={error}>
          <Button
            variant={ButtonVariant.GHOST}
            onClick={() => reset(defaults)}
            disabled={!isDirty || isSubmitting}
          >
            {t('common.cancel')}
          </Button>

          {isDraft && (
            <Button
              type="submit"
              variant={ButtonVariant.SECONDARY}
              onClick={() => {
                intent.current = 'SAVE'
              }}
              disabled={isSubmitting}
            >
              {isSubmitting ? t('common.saving') : t('jobs.create.saveDraft')}
            </Button>
          )}

          <Button
            type="submit"
            variant={ButtonVariant.PRIMARY}
            onClick={() => {
              intent.current = isDraft ? 'PUBLISH' : 'SAVE'
            }}
            disabled={isSubmitting}
          >
            {isDraft
              ? isSubmitting
                ? t('jobs.create.publishing')
                : t('jobs.manage.publish')
              : isSubmitting
                ? t('common.saving')
                : t('common.saveChanges')}
          </Button>
        </SaveBar>
      </form>

      {job.viewerCanDelete && (
        <section className="job-manage-danger">
          <h5 className="job-manage-danger-title">{t('jobs.manage.delete.title')}</h5>
          <p className="job-manage-danger-hint">{t('jobs.manage.delete.hint')}</p>

          <div className="job-manage-danger-actions">
            <Button variant={ButtonVariant.DANGER} onClick={() => setDeleting(true)}>
              {t('jobs.manage.delete.button')}
            </Button>
          </div>
        </section>
      )}

      <DeleteJobDialog job={job} isOpen={deleting} onClose={() => setDeleting(false)} />
      <UnsavedChangesModal guard={guard} />
    </SectionPanel>
  )
}

export default PostingSection
