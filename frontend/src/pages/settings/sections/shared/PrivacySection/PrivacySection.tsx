import './PrivacySection.scss'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { IconCheck } from '@tabler/icons-react'
import { parseProblem } from '@utils/api/types'
import { updatePrivacy, type PrivacyDto } from '@utils/api/settings'
import { SettingsSaveBar } from '../../../components/SettingsSaveBar'
import { SettingsSection } from '../../../components/SettingsSection'
import { UnsavedChangesModal } from '../../../components/UnsavedChangesModal'
import { canEditOrgProfile } from '../../../lib/permissions'
import { useSettingsContext } from '../../../lib/settingsOutletContext'
import useUnsavedGuard from '../../../lib/useUnsavedGuard'

const PrivacySection = () => {
  const { t } = useTranslation()
  const context = useSettingsContext()

  const canEdit = context.kind === 'user' || canEditOrgProfile(context.settings.viewerRole)

  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    getValues,
    formState: { isDirty, isSubmitting },
  } = useForm<PrivacyDto>({ defaultValues: context.settings.privacy })

  const persist = async (values: PrivacyDto): Promise<boolean> => {
    setError(null)
    try {
      await updatePrivacy(context.settings.id, values)
      reset(values) // the saved values become the new baseline -> no longer dirty
      await context.refetch()
      return true
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty,
    onSave: () => persist(getValues()),
    onDiscard: () => reset(context.settings.privacy),
  })

  return (
    <SettingsSection title={t('settings.privacy.title')} className="privacy-section">
      <form className="settings-form" onSubmit={handleSubmit(persist)} noValidate>
        {!canEdit && <p className="settings-note">{t('settings.readOnly')}</p>}

        <label className="privacy-toggle">
          <input
            type="checkbox"
            className="privacy-toggle-input"
            disabled={!canEdit}
            {...register('showJobPostings')}
          />
          <span className="privacy-toggle-box" aria-hidden>
            <IconCheck />
          </span>
          <span className="privacy-toggle-text">
            <span className="privacy-toggle-label">{t('settings.privacy.showJobPostings')}</span>
            <span className="privacy-toggle-hint">{t('settings.privacy.showJobPostingsHint')}</span>
          </span>
        </label>

        {canEdit && (
          <SettingsSaveBar isDirty={isDirty} isSaving={isSubmitting} error={error} />
        )}
      </form>

      <UnsavedChangesModal guard={guard} />
    </SettingsSection>
  )
}

export default PrivacySection
