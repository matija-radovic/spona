import './DeleteJobDialog.scss'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ConfirmDialog } from '@shared/components'
import { useUser } from '@shared/contexts'
import { actorPath } from '@utils/actorPath'
import { deleteJob, type JobDetailDto } from '@utils/api/jobs'
import { parseProblem } from '@utils/api/types'

const publisherProfile = (job: JobDetailDto, username: string | undefined): string => {
  if (job.createdFor.actorType === 'org') return actorPath(job.createdFor)
  return username ? `/users/${username}` : '/jobs'
}

interface DeleteJobDialogProps {
  job: JobDetailDto
  isOpen: boolean
  onClose: () => void
}

const DeleteJobDialog = ({ job, isOpen, onClose }: DeleteJobDialogProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useUser()

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const count = job.applicationCount ?? 0

  const close = () => {
    if (busy) return
    setError(null)
    onClose()
  }

  const confirm = async () => {
    setBusy(true)
    setError(null)
    try {
      await deleteJob(job.id)
      navigate(publisherProfile(job, user?.username), {
        replace: true,
        state: { tab: 'jobPostings' },
      })
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
      setBusy(false)
    }
  }

  return (
    <ConfirmDialog
      isOpen={isOpen}
      onClose={close}
      onConfirm={confirm}
      busy={busy}
      danger
      title={t('jobs.manage.delete.confirmTitle')}
      confirmLabel={t('jobs.manage.delete.confirm')}
      body={
        <>
          <p>
            {count > 0
              ? t('jobs.manage.delete.bodyWithApplications', { title: job.title, count })
              : t('jobs.manage.delete.body', { title: job.title })}
          </p>
          {error && <p className="job-delete-error">{error}</p>}
        </>
      }
      className="job-delete-dialog"
    />
  )
}

export default DeleteJobDialog
