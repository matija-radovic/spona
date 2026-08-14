import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { AvatarPicker, ContactInfoWidget, TextField } from '@shared/components'
import { useUser } from '@shared/contexts'
import { parseProblem } from '@utils/api/types'
import { updateProfile } from '@utils/api/settings'
import { SettingsSaveBar } from '../../../components/SettingsSaveBar'
import { SettingsSection } from '../../../components/SettingsSection'
import { UnsavedChangesModal } from '../../../components/UnsavedChangesModal'
import { useUserSettingsContext } from '../../../lib/settingsOutletContext'
import useUnsavedGuard from '../../../lib/useUnsavedGuard'
import {
  buildUserProfileSchema,
  toUpdateProfilePayload,
  toUserProfileData,
  type UserProfileData,
} from './schema'

const UserProfileSection = () => {
  const { t } = useTranslation()
  const { settings, refetch } = useUserSettingsContext()
  const { user, login } = useUser()

  const [error, setError] = useState<string | null>(null)

  const schema = useMemo(() => buildUserProfileSchema(t), [t])
  const defaults = useMemo(() => toUserProfileData(settings), [settings])

  const {
    register,
    control,
    handleSubmit,
    reset,
    getValues,
    trigger,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<UserProfileData>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: defaults,
  })

  const persist = async (values: UserProfileData): Promise<boolean> => {
    setError(null)
    try {
      const updated = await updateProfile(toUpdateProfilePayload(values))
      reset(toUserProfileData(updated))
      if (user) {
        login({ ...user, fullName: updated.fullName, avatarUrl: updated.avatarUrl ?? '' })
      }
      await refetch()
      return true
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty,
    onSave: async () => ((await trigger()) ? persist(getValues()) : false),
    onDiscard: () => reset(defaults),
  })

  return (
    <SettingsSection title={t('settings.profile.title')} description={t('settings.profile.hint')}>
      <form className="settings-form" onSubmit={handleSubmit(persist)} noValidate>
        <Controller
          control={control}
          name="avatarUrl"
          render={({ field }) => (
            <AvatarPicker label={t('auth.avatar')} value={field.value} onChange={field.onChange} />
          )}
        />

        <TextField
          label={t('auth.fullName')}
          autoComplete="name"
          error={errors.fullName?.message}
          {...register('fullName')}
        />

        <TextField
          label={t('auth.bio')}
          multiline
          placeholder={t('auth.bioPlaceholder')}
          error={errors.bio?.message}
          {...register('bio')}
        />

        <TextField
          label={t('auth.location')}
          autoComplete="address-level2"
          error={errors.location?.message}
          {...register('location')}
        />

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

        <SettingsSaveBar isDirty={isDirty} isSaving={isSubmitting} error={error} />
      </form>

      <UnsavedChangesModal guard={guard} />
    </SettingsSection>
  )
}

export default UserProfileSection
