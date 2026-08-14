import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  IconArrowLeft,
  IconBuilding,
  IconMapPin,
  IconCoin,
  IconHeart,
  IconHeartFilled,
  IconExternalLink,
  IconUpload,
} from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components/Button'
import { useUser } from '@shared/contexts'
import useFormat from '@shared/hooks/useFormat'
import { type JobDetailDto } from '@utils/api/jobs'
import { isJobSaved, toggleSavedJob } from '@utils/savedJobs'
import { formatSalary } from '@pages/jobs/lib/formatSalary'
import { useCvApply } from '@pages/jobs/lib/useCvApply'
import { ContactApply } from '@pages/jobs/components/ContactApply'
import { CvDropApply } from '@pages/jobs/components/CvDropApply'

interface JobDetailContentProps {
  job: JobDetailDto
  isMobile: boolean
  onBack: () => void
}

const JobDetailContent = ({ job, isMobile, onBack }: JobDetailContentProps) => {
  const { t } = useTranslation()
  const format = useFormat()
  const { isAuthenticated } = useUser()
  const cv = useCvApply(job.id)

  const [favorited, setFavorited] = useState(() => isJobSaved(job.id))

  // TODO(auth-preview): flip/comment this guard to preview the heart while logged out
  const canFavorite = isAuthenticated  /* || true */

  const onToggleFavorite = () => {
    setFavorited(toggleSavedJob({ id: job.id, slug: job.slug }))
  }

  const { createdFor } = job
  const isOrg = createdFor.actorType === 'org'
  // The drop target is the WHOLE panel, not just the apply row.
  const isCvDrop = job.applicationType === 'CV_DROP'

  return (
    <article className="job-detail" {...(isCvDrop ? cv.dropHandlers : {})}>
      {isCvDrop && cv.dragActive && (
        <div className="job-detail-drop-overlay" aria-hidden>
          <div className="job-detail-drop-inner">
            <IconUpload />
            <span>{t('jobs.apply.dropHere')}</span>
          </div>
        </div>
      )}

      <div className="job-detail-topbar">
        {isMobile && (
          <button type="button" className="job-detail-back" onClick={onBack} aria-label={t('jobs.panel.back')}>
            <IconArrowLeft aria-hidden />
          </button>
        )}
        {canFavorite && (
          <button
            type="button"
            className={`job-detail-fav${favorited ? ' is-active' : ''}`}
            onClick={onToggleFavorite}
            aria-pressed={favorited}
            aria-label={favorited ? t('jobs.favorite.remove') : t('jobs.favorite.add')}
          >
            {favorited ? <IconHeartFilled /> : <IconHeart />}
          </button>
        )}
      </div>

      <header className="job-detail-head">
        <div className="job-detail-head-main">
          <h2 className="job-detail-title">{job.title}</h2>
          <span className="job-detail-poster">
            {isOrg ? createdFor.displayName : t('jobs.card.privatePoster')}
          </span>

          <div className="job-detail-meta">
            {job.location && (
              <span className="job-detail-meta-item">
                <IconMapPin aria-hidden />
                {job.location}
              </span>
            )}
            {job.salary && (
              <span className="job-detail-meta-item">
                <IconCoin aria-hidden />
                {formatSalary(job.salary, t, format.locale)}
              </span>
            )}
          </div>

          <div className="job-detail-tags">
            <span className="job-detail-tag">{t(`jobs.type.${job.jobType}`)}</span>
            <span className="job-detail-tag">{t(`jobs.workplace.${job.workplaceType}`)}</span>
            <span className="job-detail-tag">{t(`jobs.experience.${job.experienceLevel}`)}</span>
          </div>
        </div>

        {isOrg && (
          <span className="job-detail-logo">
            {createdFor.avatarUrl ? (
              <img src={createdFor.avatarUrl} alt={t('jobs.card.companyLogoAlt')} />
            ) : (
              <IconBuilding aria-hidden />
            )}
          </span>
        )}
      </header>

      <section className="job-detail-apply">
        {job.applicationType === 'CONTACT_ONLY' && (
          <>
            <h3 className="job-detail-apply-title">{t('jobs.contactEmployer')}</h3>
            <ContactApply entries={job.contactInformation ?? []} />
          </>
        )}

        {job.applicationType === 'EXTERNAL_URL' && job.externalUrl && (
          <Button
            variant={ButtonVariant.PRIMARY}
            href={job.externalUrl}
            external
            className="job-detail-external"
          >
            {t('jobs.applyExternal')}
            <IconExternalLink aria-hidden />
          </Button>
        )}

        {isCvDrop && <CvDropApply cv={cv} />}
      </section>

      <section className="job-detail-description">{job.description}</section>
    </article>
  )
}

export default JobDetailContent
