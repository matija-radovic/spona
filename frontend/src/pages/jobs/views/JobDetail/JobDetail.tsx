import './JobDetail.scss'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconArrowLeft } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components/Button'
import { fetchJobBySlug, type JobDetailDto } from '@utils/api/jobs'
import { parseProblem, type ProblemDetail } from '@utils/api/types'
import { useJobsOutletContext } from '@pages/jobs/lib/jobsOutletContext'
import JobDetailContent from './JobDetailContent'

/**
 * Fetches the job by slug and owns the loading / error / not-found states. The
 * loaded view is `JobDetailContent`, keyed by slug so navigating between jobs
 * gives it (and its CV apply state) a clean slate.
 */
const JobDetail = () => {
  const { t } = useTranslation()
  const { slug } = useParams()
  const { isMobile, detailOnly, onBack } = useJobsOutletContext()

  const [job, setJob] = useState<JobDetailDto | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  // `loadedSlug` marks which slug the job/problem correspond to; loading is
  // derived from it, so the effect only setStates in async callbacks.
  const [loadedSlug, setLoadedSlug] = useState<string | undefined>(undefined)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!slug) return
    let active = true
    fetchJobBySlug(slug)
      .then((data) => {
        if (!active) return
        setJob(data)
        setProblem(null)
        setLoadedSlug(slug)
      })
      .catch((err) => {
        if (!active) return
        setJob(null)
        setProblem(parseProblem(err))
        setLoadedSlug(slug)
      })
    return () => {
      active = false
    }
  }, [slug, reloadKey])

  const loading = loadedSlug !== slug

  if (loading || problem || !job) {
    const isNotFound = problem?.status === 404
    return (
      <div className="job-detail job-detail-status">
        {isMobile && (
          <div className="job-detail-topbar">
            <button type="button" className="job-detail-back" onClick={onBack} aria-label={t('jobs.panel.back')}>
              <IconArrowLeft aria-hidden />
            </button>
          </div>
        )}

        <div className="job-detail-status-body">
          {loading ? (
            <p>{t('common.loading')}</p>
          ) : (
            <>
              <p>{isNotFound ? t('jobs.panel.notFound') : problem?.detail || t('errors.serverError')}</p>
              {!isNotFound && (
                <Button
                  variant={ButtonVariant.TERTIARY}
                  onClick={() => {
                    setLoadedSlug(undefined)
                    setReloadKey((k) => k + 1)
                  }}
                >
                  {t('jobs.results.retry')}
                </Button>
              )}
            </>
          )}
        </div>
      </div>
    )
  }

  return (
    <JobDetailContent key={slug} job={job} isMobile={isMobile} detailOnly={detailOnly} onBack={onBack} />
  )
}

export default JobDetail
