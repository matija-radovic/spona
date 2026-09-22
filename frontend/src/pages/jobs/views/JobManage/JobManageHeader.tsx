import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconExternalLink } from '@tabler/icons-react'
import {
  ActorAvatar,
  ActorAvatarSize,
  Button,
  ButtonVariant,
  ConfirmDialog,
} from '@shared/components'
import { setJobStatus, type JobDetailDto } from '@utils/api/jobs'
import { parseProblem } from '@utils/api/types'
import { problemMessage } from '@utils/api/problemMessage'
import { JobStatusChip } from '@pages/jobs/components/JobStatusChip'

interface JobManageHeaderProps {
  job: JobDetailDto
  refetch: () => Promise<void>
}
const JobManageHeader = ({ job, refetch }: JobManageHeaderProps) => {
  const { t } = useTranslation()

  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const org = job.createdFor.actorType === 'org' ? job.createdFor : null

  const move = async (status: 'OPEN' | 'CLOSED') => {
    setBusy(true)
    setError(null)
    try {
      await setJobStatus(job.id, status)
      await refetch()
      setConfirming(false)
    } catch (caught) {
      setError(problemMessage(parseProblem(caught), t))
    } finally {
      setBusy(false)
    }
  }

  return (
    <header className="job-manage-head">
      <div className="job-manage-head-row">
        <h2 className="job-manage-title">{job.title}</h2>

        <div className="job-manage-head-actions">
          <JobStatusChip status={job.status} showOpen />

          {job.status === 'OPEN' && (
            <Button
              variant={ButtonVariant.DANGER_OUTLINE}
              onClick={() => setConfirming(true)}
              disabled={busy}
            >
              {t('jobs.manage.close')}
            </Button>
          )}

          {job.status === 'CLOSED' && (
            <Button
              variant={ButtonVariant.SECONDARY}
              onClick={() => move('OPEN')}
              disabled={busy}
            >
              {busy ? t('common.saving') : t('jobs.manage.reopen')}
            </Button>
          )}
        </div>
      </div>

      <div className="job-manage-head-row job-manage-head-sub">
        <span className="job-manage-actor">
          <ActorAvatar
            avatarUrl={org?.avatarUrl ?? null}
            actorType={org ? 'org' : 'user'}
            size={ActorAvatarSize.XS}
          />
          <span className="job-manage-actor-name">
            {t('post.postedAs')}: {org ? org.name : t('jobs.card.privatePoster')}
          </span>
          <span className="job-manage-actor-hint">{t('jobs.manage.identityFixed')}</span>
        </span>

        <Link to={`/jobs/${job.slug}`} className="job-manage-view">
          {t('jobs.manage.viewPosting')}
          <IconExternalLink aria-hidden />
        </Link>
      </div>

      {error && <p className="job-manage-head-error">{error}</p>}

      <ConfirmDialog
        isOpen={confirming}
        onClose={() => !busy && setConfirming(false)}
        onConfirm={() => move('CLOSED')}
        busy={busy}
        danger
        title={t('jobs.manage.closeConfirm.title')}
        confirmLabel={t('jobs.manage.closeConfirm.confirm')}
        body={<p>{t('jobs.manage.closeConfirm.body')}</p>}
      />
    </header>
  )
}

export default JobManageHeader
