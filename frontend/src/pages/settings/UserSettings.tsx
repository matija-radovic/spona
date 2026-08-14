import { useCallback, useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useUser } from '@shared/contexts'
import { parseProblem, type ProblemDetail } from '@utils/api/types'
import { fetchUserSettings, type UserSettingsDto } from '@utils/api/settings'
import SettingsLayout from './SettingsLayout'
import { USER_SECTIONS } from './lib/sections'
import type { UserSettingsOutletContext } from './lib/settingsOutletContext'

const UserSettings = () => {
  const { t } = useTranslation()
  const { username } = useParams()
  const { user } = useUser()

  const [settings, setSettings] = useState<UserSettingsDto | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [loadedFor, setLoadedFor] = useState<string | undefined>(undefined)

  const isSelf = !!user && !!username && user.username === username

  const load = useCallback((): Promise<void> => {
    if (!username || !isSelf) return Promise.resolve()
    return fetchUserSettings(username)
      .then((dto) => {
        setSettings(dto)
        setProblem(null)
      })
      .catch((error: unknown) => {
        setSettings(null)
        setProblem(parseProblem(error))
      })
      .finally(() => setLoadedFor(username))
  }, [username, isSelf])

  useEffect(() => {
    void load()
  }, [load])

  if (!isSelf) {
    return <Navigate to={`/users/${username}`} replace />
  }

  if (loadedFor !== username) {
    return (
      <div className="settings-layout container">
        <p className="settings-status">{t('common.loading')}</p>
      </div>
    )
  }

  if (problem || !settings) {
    return (
      <div className="settings-layout container">
        <p className="settings-status">{problem?.detail || t('errors.serverError')}</p>
      </div>
    )
  }

  const context: UserSettingsOutletContext = { kind: 'user', settings, refetch: load }

  return <SettingsLayout sections={USER_SECTIONS} context={context} />
}

export default UserSettings
