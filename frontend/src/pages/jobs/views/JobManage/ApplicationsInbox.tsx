import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconDownload } from '@tabler/icons-react'
import { ActorByline, Button, ButtonVariant } from '@shared/components'
import useFormat from '@shared/hooks/useFormat'
import useOnScreen from '@shared/hooks/useOnScreen'
import { fetchJobApplications, type JobApplicationDto } from '@utils/api/jobs'
import { safeHref } from '@utils/safeUrl'

const PAGE_SIZE = 10

type InboxState = 'loading' | 'error' | 'ready'

interface ApplicationsInboxProps {
  jobId: string
  /** `applicationCount` off the DTO */
  total: number
}

const ApplicationsInbox = ({ jobId, total }: ApplicationsInboxProps) => {
  const { t } = useTranslation()
  const format = useFormat()

  const [items, setItems] = useState<JobApplicationDto[]>([])
  const [last, setLast] = useState(false)
  const [state, setState] = useState<InboxState>('loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const itemsRef = useRef<JobApplicationDto[]>([])
  const pageRef = useRef(1)
  const lastRef = useRef(false)
  const loadingRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let alive = true
    loadingRef.current = true
    fetchJobApplications(jobId, 1, PAGE_SIZE)
      .then((res) => {
        if (!alive) return
        itemsRef.current = res.content
        pageRef.current = res.page
        lastRef.current = res.last
        setItems(res.content)
        setLast(res.last)
        setState('ready')
      })
      .catch(() => {
        if (alive) setState('error')
      })
      .finally(() => {
        loadingRef.current = false
      })
    return () => {
      alive = false
    }
  }, [jobId, reloadKey])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchJobApplications(jobId, pageRef.current + 1, PAGE_SIZE)
      const merged = [...itemsRef.current, ...res.content]
      itemsRef.current = merged
      pageRef.current = res.page
      lastRef.current = res.last
      setItems(merged)
      setLast(res.last)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [jobId])

  const canLoadMore = state === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const body = () => {
    if (state === 'loading') {
      return <p className="job-manage-inbox-state">{t('common.loading')}</p>
    }

    if (state === 'error') {
      return (
        <div className="job-manage-inbox-state">
          <p>{t('errors.serverError')}</p>
          <Button
            variant={ButtonVariant.TERTIARY}
            onClick={() => {
              setState('loading')
              setLoadMoreError(false)
              setReloadKey((key) => key + 1)
            }}
          >
            {t('jobs.results.retry')}
          </Button>
        </div>
      )
    }

    if (items.length === 0) {
      return <p className="job-manage-inbox-state">{t('jobs.manage.inbox.empty')}</p>
    }

    return (
      <>
        <table className="job-manage-inbox-table">
          <thead>
            <tr>
              <th scope="col">{t('jobs.manage.inbox.candidate')}</th>
              <th scope="col">{t('jobs.manage.inbox.submitted')}</th>
              <th scope="col">{t('jobs.manage.inbox.cv')}</th>
            </tr>
          </thead>

          <tbody>
            {items.map((application) => {
              const href = safeHref(application.cvUrl)
              return (
                <tr key={application.id} className="job-manage-inbox-row">
                  <td
                    className="job-manage-inbox-cell-candidate"
                    data-label={t('jobs.manage.inbox.candidate')}
                  >
                    <ActorByline
                      actor={application.applicant}
                      createdAt={application.submittedAt}
                    />
                  </td>

                  <td data-label={t('jobs.manage.inbox.submitted')}>
                    {format.date(application.submittedAt)}
                  </td>

                  <td data-label={t('jobs.manage.inbox.cv')}>
                    {href && (
                      <a
                        className="job-manage-inbox-cv"
                        href={href}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <IconDownload aria-hidden />
                        {t('jobs.manage.inbox.download')}
                      </a>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>

        <div ref={sentinelRef} className="job-manage-inbox-sentinel" aria-hidden />

        {loadingMore && <p className="job-manage-inbox-more">{t('common.loading')}</p>}

        {loadMoreError && (
          <div className="job-manage-inbox-more">
            <span>{t('jobs.results.loadMoreError')}</span>
            <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
              {t('jobs.results.retry')}
            </Button>
          </div>
        )}
      </>
    )
  }

  return (
    <section className="job-manage-inbox">
      <header className="job-manage-inbox-head">
        <h5 className="job-manage-inbox-title">{t('jobs.manage.inbox.title')}</h5>
        {total > 0 && (
          <span className="job-manage-inbox-count">
            {t('jobs.manage.inbox.count', { count: total })}
          </span>
        )}
      </header>

      {body()}
    </section>
  )
}

export default ApplicationsInbox
