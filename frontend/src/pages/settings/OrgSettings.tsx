import './Settings.scss'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { SectionLayout } from '@shared/components'
import { parseProblem, type ProblemDetail } from '@utils/api/types'
import { problemMessage } from '@utils/api/problemMessage'
import { fetchOrgSettings, type OrgSettingsDto } from '@utils/api/organizations'
import { ORG_SECTIONS, OWNER_ONLY_SECTION } from './lib/sections'
import { isOwner } from './lib/permissions'
import type { OrgSettingsOutletContext } from './lib/settingsOutletContext'

const OrgSettings = () => {
  const { t } = useTranslation()
  const { slug } = useParams()

  const [settings, setSettings] = useState<OrgSettingsDto | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [loadedFor, setLoadedFor] = useState<string | undefined>(undefined)

  const load = useCallback((): Promise<void> => {
    if (!slug) return Promise.resolve()
    return fetchOrgSettings(slug)
      .then((dto) => {
        setSettings(dto)
        setProblem(null)
      })
      .catch((error: unknown) => {
        setSettings(null)
        setProblem(parseProblem(error))
      })
      .finally(() => setLoadedFor(slug))
  }, [slug])

  useEffect(() => {
    void load()
  }, [load])

  const sections = useMemo(
    () =>
      settings && isOwner(settings.viewerRole)
        ? ORG_SECTIONS
        : ORG_SECTIONS.filter((section) => section.path !== OWNER_ONLY_SECTION),
    [settings],
  )

  if (loadedFor !== slug) {
    return (
      <div className="settings-layout container">
        <p className="settings-status">{t('common.loading')}</p>
      </div>
    )
  }

  if (problem?.status === 403 || problem?.status === 404) {
    return <Navigate to={`/companies/${slug}`} replace />
  }

  if (problem || !settings) {
    return (
      <div className="settings-layout container">
        <p className="settings-status">{problemMessage(problem, t)}</p>
      </div>
    )
  }

  const context: OrgSettingsOutletContext = { kind: 'org', settings, refetch: load }

  return (
    <SectionLayout
      sections={sections}
      context={context}
      navLabel={t('nav.settings')}
      className="container settings-layout"
    />
  )
}

export default OrgSettings
