import './JobDetail.scss'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconArrowLeft } from '@tabler/icons-react'
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
  const { isMobile, onBack } = useJobsOutletContext()

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
        <div className="job-detail-topbar">
          {isMobile && (
            <button type="button" className="job-detail-back" onClick={onBack} aria-label={t('jobs.panel.back')}>
              <IconArrowLeft size={20} aria-hidden />
            </button>
          )}
        </div>

        <div className="job-detail-status-body">
          {loading ? (
            <p className="job-detail-muted">{t('common.loading')}</p>
          ) : (
            <>
              <p className="job-detail-muted">
                {isNotFound ? t('jobs.panel.notFound') : problem?.detail || t('errors.serverError')}
              </p>
              {!isNotFound && (
                <button
                  type="button"
                  className="job-detail-retry"
                  onClick={() => {
                    setLoadedSlug(undefined)
                    setReloadKey((k) => k + 1)
                  }}
                >
                  {t('jobs.results.retry')}
                </button>
              )}
            </>
          )}
        </div>
      </div>
    )
  }

  return <JobDetailContent key={slug} job={job} isMobile={isMobile} onBack={onBack} />
}

export default JobDetail
