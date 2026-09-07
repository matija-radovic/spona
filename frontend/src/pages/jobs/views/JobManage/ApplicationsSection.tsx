import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import {
  Button,
  ButtonVariant,
  SaveBar,
  SectionPanel,
  UnsavedChangesModal,
} from '@shared/components'
import type { ContactEntry } from '@shared/components'
import { useUser } from '@shared/contexts'
import useUnsavedGuard from '@shared/hooks/useUnsavedGuard'
import { updateJob } from '@utils/api/jobs'
import { fetchOrgSettings } from '@utils/api/organizations'
import { fetchUserSettings } from '@utils/api/settings'
import { parseProblem } from '@utils/api/types'
import { ApplyFields } from '@pages/jobs/components/ApplyFields'
import {
  buildJobSchema,
  toJobFormData,
  toUpdateJobPayload,
  type JobFormData,
} from '@pages/jobs/lib/jobSchema'
import ApplicationsInbox from './ApplicationsInbox'
import { useJobManageContext } from './jobManageContext'

const ApplicationsSection = () => {
  const { t } = useTranslation()
  const { job, refetch } = useJobManageContext()
  const { user } = useUser()

  const [savedContacts, setSavedContacts] = useState<readonly ContactEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  const isDraft = job.status === 'DRAFT'
  const isCvDrop = job.applicationType === 'CV_DROP'
  const isEditable = isDraft || !isCvDrop

  const schema = useMemo(() => buildJobSchema(t, isDraft), [t, isDraft])
  const defaults = useMemo(() => toJobFormData(job), [job])

  const {
    register,
    control,
    handleSubmit,
    reset,
    trigger,
    getValues,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<JobFormData>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: defaults,
  })

  const publisherSlug = job.createdFor.actorType === 'org' ? job.createdFor.handle : null

  useEffect(() => {
    const pending = publisherSlug
      ? fetchOrgSettings(publisherSlug)
      : user
        ? fetchUserSettings(user.username)
        : null
    if (!pending) return

    let alive = true
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
  }, [publisherSlug, user])

  const persist = async (values: JobFormData): Promise<boolean> => {
    setError(null)
    try {
      const saved = await updateJob(job.id, toUpdateJobPayload(values))
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
    onSave: async () => ((await trigger()) ? persist(getValues()) : false),
    onDiscard: () => reset(defaults),
  })

  const fields = (
    <ApplyFields
      register={register}
      control={control}
      errors={errors}
      savedContacts={savedContacts}
      typeLocked={!isDraft}
      cvDropPanel={
        isDraft ? undefined : <ApplicationsInbox jobId={job.id} total={job.applicationCount ?? 0} />
      }
    />
  )

  return (
    <SectionPanel
      title={t('jobs.manage.apply.title')}
      description={isDraft ? t('jobs.manage.apply.draftHint') : undefined}
      className="job-manage-apply"
    >
      {isEditable ? (
        <form
          className="job-manage-form"
          onSubmit={(event) => handleSubmit(persist)(event)}
          noValidate
        >
          {fields}

          <SaveBar error={error}>
            <Button
              variant={ButtonVariant.GHOST}
              onClick={() => reset(defaults)}
              disabled={!isDirty || isSubmitting}
            >
              {t('common.cancel')}
            </Button>

            <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
              {isSubmitting ? t('common.saving') : t('common.saveChanges')}
            </Button>
          </SaveBar>
        </form>
      ) : (
        fields
      )}

      <UnsavedChangesModal guard={guard} />
    </SectionPanel>
  )
}

export default ApplicationsSection
