import { useState } from 'react'
import { Link } from 'react-router-dom'
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
import { ActionMenu, Button, ButtonVariant, contentMenuItems, MenuAlign } from '@shared/components'
import { useUser } from '@shared/contexts'
import { MQ } from '@shared/css/mediaqueries'
import useFormat from '@shared/hooks/useFormat'
import useMediaQuery from '@shared/hooks/useMediaQuery'
import { actorPath } from '@utils/actorPath'
import { type JobDetailDto } from '@utils/api/jobs'
import { isJobSaved, toggleSavedJob } from '@utils/savedJobs'
import { formatSalary } from '@pages/jobs/lib/formatSalary'
import { useCvApply } from '@pages/jobs/lib/useCvApply'
import { ContactApply } from '@pages/jobs/components/ContactApply'
import { CvDropApply } from '@pages/jobs/components/CvDropApply'
import { JobRail } from '@pages/jobs/components/JobRail'

interface JobDetailContentProps {
  job: JobDetailDto
  isMobile: boolean
  detailOnly: boolean
  onBack: () => void
}

const JobDetailContent = ({ job, isMobile, detailOnly, onBack }: JobDetailContentProps) => {
  const { t } = useTranslation()
  const format = useFormat()
  const { isAuthenticated } = useUser()
  const cv = useCvApply(job.id)
  const wideEnoughForRail = useMediaQuery(MQ.exceptTablet)

  const [favorited, setFavorited] = useState(() => isJobSaved(job.id))

  // TODO(auth-preview): flip/comment this guard to preview the heart while logged out
  const canFavorite = isAuthenticated  /* || true */

  const onToggleFavorite = () => {
    setFavorited(toggleSavedJob({ id: job.id, slug: job.slug }))
  }

  const { createdFor } = job
  const org = createdFor.actorType === 'org' ? createdFor : null
  // The drop target is the WHOLE panel, not just the apply row.
  const isCvDrop = job.applicationType === 'CV_DROP'
  const rail = detailOnly && wideEnoughForRail ? org : null

  const actions = (
    <div className="job-detail-topbar-actions">
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

      <ActionMenu
        align={MenuAlign.END}
        className="job-detail-menu"
        items={contentMenuItems({
          path: `/jobs/${job.slug}`,
          copyLabel: t('common.copyLink'),
        })}
      />
    </div>
  )

  return (
    <div className="job-detail-page">
      <article
        className={`job-detail${rail ? ' is-column' : ''}`}
        {...(isCvDrop ? cv.dropHandlers : {})}
      >
        {isCvDrop && cv.dragActive && (
          <div className="job-detail-drop-overlay" aria-hidden>
            <div className="job-detail-drop-inner">
              <IconUpload />
              <span>{t('jobs.apply.dropHere')}</span>
            </div>
          </div>
        )}

        {isMobile && (
          <div className="job-detail-topbar">
            <button type="button" className="job-detail-back" onClick={onBack} aria-label={t('jobs.panel.back')}>
              <IconArrowLeft aria-hidden />
            </button>
            {actions}
          </div>
        )}

        <header className="job-detail-head">
          <div className="job-detail-head-main">
            <div className="job-detail-title-row">
              <h2 className="job-detail-title">{job.title}</h2>
              {job.status !== 'OPEN' && (
                <span className={`job-detail-status-chip is-${job.status.toLowerCase()}`}>
                  {t(`jobs.status.${job.status}`)}
                </span>
              )}
            </div>

            {org ? (
              <Link to={actorPath(org)} className="job-detail-poster">
                {org.name}
              </Link>
            ) : (
              <span className="job-detail-poster is-private">{t('jobs.card.privatePoster')}</span>
            )}

            {(job.location || job.salary) && (
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
            )}

            <div className="job-detail-tags">
              <span className="job-detail-tag">{t(`jobs.type.${job.jobType}`)}</span>
              <span className="job-detail-tag">{t(`jobs.workplace.${job.workplaceType}`)}</span>
              <span className="job-detail-tag">{t(`jobs.experience.${job.experienceLevel}`)}</span>
            </div>
          </div>

          {(!isMobile || org) && (
            <div className="job-detail-head-side">
              {!isMobile && actions}

              {org && (
                <span className="job-detail-logo">
                  {org.avatarUrl ? (
                    <img src={org.avatarUrl} alt={t('jobs.card.companyLogoAlt')} />
                  ) : (
                    <IconBuilding aria-hidden />
                  )}
                </span>
              )}
            </div>
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

        {job.keywords && job.keywords.length > 0 && (
          <p className="job-detail-keywords">
            {t('jobs.keywords')}: {job.keywords.join(' · ')}
          </p>
        )}
      </article>

      {rail && (
        <JobRail
          org={rail}
          bio={job.createdForBio}
          followedByViewer={job.createdForFollowedByViewer}
        />
      )}
    </div>
  )
}

export default JobDetailContent
