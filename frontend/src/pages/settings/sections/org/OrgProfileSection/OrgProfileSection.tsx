import './OrgProfileSection.scss'
import { useMemo, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import {
  ActorAvatar,
  ActorAvatarSize,
  AvatarPicker,
  Button,
  ButtonVariant,
  ContactInfoWidget,
  ContactValue,
  contactTypeOption,
  FieldShell,
  Input,
  SaveBar,
  SectionPanel,
  UnsavedChangesModal,
} from '@shared/components'
import useUnsavedGuard from '@shared/hooks/useUnsavedGuard'
import { parseProblem } from '@utils/api/types'
import { updateOrgProfile } from '@utils/api/organizations'
import { resizeImageToSquare } from '@utils/image/resizeImage'
import { canEditOrgProfile } from '../../../lib/permissions'
import { useOrgSettingsContext } from '../../../lib/settingsOutletContext'
import {
  buildOrgProfileSchema,
  toOrgProfileData,
  toUpdateOrgPayload,
  type OrgProfileData,
} from './schema'

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

  const avatarUrl = useWatch({ control, name: 'avatarUrl' })

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
    <SectionPanel
      title={t('settings.profile.title')}
      description={t('settings.profile.orgHint')}
      className="org-profile-section"
    >
      <form className="settings-form" onSubmit={handleSubmit(persist)} noValidate>
        {!canEdit && <p className="settings-note">{t('settings.readOnly')}</p>}

        {canEdit ? (
          <Controller
            control={control}
            name="avatar"
            render={({ field }) => (
              <AvatarPicker
                label={t('auth.avatar')}
                value={field.value || avatarUrl}
                onChange={async (file) => field.onChange(await resizeImageToSquare(file))}
              />
            )}
          />
        ) : (
          <FieldShell label={t('auth.avatar')} htmlLabel={false}>
            <ActorAvatar
              avatarUrl={settings.avatarUrl}
              actorType="org"
              size={ActorAvatarSize.MD}
              alt={settings.displayName}
            />
          </FieldShell>
        )}

        <FieldShell label={t('auth.org.displayName')} required>
          <Input
            readOnly={!canEdit}
            error={errors.displayName?.message}
            {...register('displayName')}
          />
        </FieldShell>

        <FieldShell label={t('auth.org.legalName')} required>
          <Input
            readOnly={!canEdit}
            error={errors.legalName?.message}
            {...register('legalName')}
          />
        </FieldShell>

        <FieldShell label={t('auth.org.description')}>
          <Input
            multiline
            readOnly={!canEdit}
            error={errors.description?.message}
            {...register('description')}
          />
        </FieldShell>

        <FieldShell label={t('auth.org.website')}>
          <Input
            type="url"
            placeholder="https://"
            readOnly={!canEdit}
            error={errors.website?.message}
            {...register('website')}
          />
        </FieldShell>

        <FieldShell label={t('auth.org.location')}>
          <Input
            readOnly={!canEdit}
            error={errors.location?.message}
            {...register('location')}
          />
        </FieldShell>

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
          <FieldShell label={t('auth.otherContactInfo')} htmlLabel={false}>
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
          </FieldShell>
        )}

        {canEdit && (
          <SaveBar error={error}>
            <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={!isDirty || isSubmitting}>
              {isSubmitting ? t('common.saving') : t('common.saveChanges')}
            </Button>
          </SaveBar>
        )}
      </form>

      <UnsavedChangesModal guard={guard} />
    </SectionPanel>
  )
}

export default OrgProfileSection
