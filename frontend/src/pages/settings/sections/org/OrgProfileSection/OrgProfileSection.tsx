import './OrgProfileSection.scss'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import {
  ActorAvatar,
  ActorAvatarSize,
  AvatarPicker,
  ContactInfoWidget,
  ContactValue,
  contactTypeOption,
  TextField,
} from '@shared/components'
import { parseProblem } from '@utils/api/types'
import { updateOrgProfile } from '@utils/api/organizations'
import { uploadAvatar } from '@utils/api/uploads'
import { SettingsSaveBar } from '../../../components/SettingsSaveBar'
import { SettingsSection } from '../../../components/SettingsSection'
import { UnsavedChangesModal } from '../../../components/UnsavedChangesModal'
import { canEditOrgProfile } from '../../../lib/permissions'
import { useOrgSettingsContext } from '../../../lib/settingsOutletContext'
import useUnsavedGuard from '../../../lib/useUnsavedGuard'
import {
  buildOrgProfileSchema,
  toOrgProfileData,
  toUpdateOrgPayload,
  type OrgProfileData,
} from './schema'

/**
 * EDITOR and RECRUITER get the same layout read-only. `AvatarPicker` and
 * `ContactInfoWidget` have no read-only mode, so those two are swapped for
 * `ActorAvatar` and `ContactValue` rather than mounted and disabled.
 */
const OrgProfileSection = () => {
  const { t } = useTranslation()
  const { settings, refetch } = useOrgSettingsContext()

  const canEdit = canEditOrgProfile(settings.viewerRole)
  const [error, setError] = useState<string | null>(null)

  const schema = useMemo(() => buildOrgProfileSchema(t), [t])
  const defaults = useMemo(() => toOrgProfileData(settings), [settings])

  const {
    register,
    control,
    handleSubmit,
    reset,
    getValues,
    trigger,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<OrgProfileData>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: defaults,
  })

  const persist = async (values: OrgProfileData): Promise<boolean> => {
    setError(null)
    try {
      const updated = await updateOrgProfile(settings.slug, toUpdateOrgPayload(values))
      reset(toOrgProfileData(updated))
      await refetch()
      return true
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty: canEdit && isDirty,
    onSave: async () => ((await trigger()) ? persist(getValues()) : false),
    onDiscard: () => reset(defaults),
  })

  return (
    <SettingsSection
      title={t('settings.profile.title')}
      description={t('settings.profile.orgHint')}
      className="org-profile-section"
    >
      <form className="settings-form" onSubmit={handleSubmit(persist)} noValidate>
        {!canEdit && <p className="settings-note">{t('settings.readOnly')}</p>}

        {canEdit ? (
          <Controller
            control={control}
            name="avatarUrl"
            render={({ field }) => (
              <AvatarPicker
                label={t('auth.avatar')}
                value={field.value}
                onChange={async (file) => field.onChange(await uploadAvatar(file))}
              />
            )}
          />
        ) : (
          <div className="org-profile-avatar">
            <span className="org-profile-avatar-label">{t('auth.avatar')}</span>
            <ActorAvatar
              avatarUrl={settings.avatarUrl}
              actorType="org"
              size={ActorAvatarSize.MD}
              alt={settings.displayName}
            />
          </div>
        )}

        <TextField
          label={t('auth.org.displayName')}
          required
          readOnly={!canEdit}
          error={errors.displayName?.message}
          {...register('displayName')}
        />

        <TextField
          label={t('auth.org.legalName')}
          required
          readOnly={!canEdit}
          error={errors.legalName?.message}
          {...register('legalName')}
        />

        <TextField
          label={t('auth.org.description')}
          multiline
          readOnly={!canEdit}
          error={errors.description?.message}
          {...register('description')}
        />

        <TextField
          label={t('auth.org.website')}
          type="url"
          placeholder="https://"
          readOnly={!canEdit}
          error={errors.website?.message}
          {...register('website')}
        />

        <TextField
          label={t('auth.org.location')}
          readOnly={!canEdit}
          error={errors.location?.message}
          {...register('location')}
        />

        {canEdit ? (
          <Controller
            control={control}
            name="otherContactInfo"
            render={({ field }) => (
              <ContactInfoWidget
                label={t('auth.otherContactInfo')}
                value={field.value}
                onChange={field.onChange}
              />
            )}
          />
        ) : (
          <div className="org-profile-contacts">
            <span className="org-profile-contacts-label">{t('auth.otherContactInfo')}</span>
            <ul className="org-profile-contacts-list">
              {settings.otherContactInfo.map((entry, index) => {
                const option = contactTypeOption(entry.type)
                const EntryIcon = option.icon
                return (
                  <li key={`${entry.type}-${index}`} className="org-profile-contact">
                    <EntryIcon aria-hidden />
                    <span className="org-profile-contact-desc">
                      {entry.description || t(option.labelKey)}
                    </span>
                    <ContactValue entry={entry} className="org-profile-contact-value" />
                  </li>
                )
              })}
            </ul>
          </div>
        )}

        {canEdit && (
          <SettingsSaveBar isDirty={isDirty} isSaving={isSubmitting} error={error} />
        )}
      </form>

      <UnsavedChangesModal guard={guard} />
    </SettingsSection>
  )
}

export default OrgProfileSection
