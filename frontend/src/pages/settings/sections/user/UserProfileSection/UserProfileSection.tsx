import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import {
  AvatarPicker,
  Button,
  ButtonVariant,
  ContactInfoWidget,
  FieldShell,
  Input,
  SaveBar,
  SectionPanel,
  UnsavedChangesModal,
} from '@shared/components'
import { useUser } from '@shared/contexts'
import useUnsavedGuard from '@shared/hooks/useUnsavedGuard'
import { parseProblem } from '@utils/api/types'
import { updateProfile } from '@utils/api/settings'
import { uploadAvatar } from '@utils/api/uploads'
import { useUserSettingsContext } from '../../../lib/settingsOutletContext'
import {
  buildUserProfileSchema,
  toUpdateProfilePayload,
  toUserProfileData,
  type UserProfileData,
} from './schema'

const UserProfileSection = () => {
  const { t } = useTranslation()
  const { settings, refetch } = useUserSettingsContext()
  const { user, updateUser } = useUser()

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
        updateUser({ ...user, fullName: updated.fullName, avatarUrl: updated.avatarUrl ?? '' })
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
    <SectionPanel title={t('settings.profile.title')} description={t('settings.profile.hint')}>
      <form className="settings-form" onSubmit={handleSubmit(persist)} noValidate>
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

        <FieldShell label={t('auth.fullName')}>
          <Input autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
        </FieldShell>

        <FieldShell label={t('auth.bio')}>
          <Input
            multiline
            placeholder={t('auth.bioPlaceholder')}
            error={errors.bio?.message}
            {...register('bio')}
          />
        </FieldShell>

        <FieldShell label={t('auth.location')}>
          <Input
            autoComplete="address-level2"
            error={errors.location?.message}
            {...register('location')}
          />
        </FieldShell>

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

        <SaveBar error={error}>
          <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={!isDirty || isSubmitting}>
            {isSubmitting ? t('common.saving') : t('common.saveChanges')}
          </Button>
        </SaveBar>
      </form>

      <UnsavedChangesModal guard={guard} />
    </SectionPanel>
  )
}

export default UserProfileSection
