import { Controller, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { AvatarPicker, ContactInfoWidget, FieldShell, Input } from '@shared/components'
import { uploadAvatar } from '@utils/api/uploads'
import type { RegisterData } from '../schema'

interface StagePersonalProps {
  form: UseFormReturn<RegisterData>
}

const StagePersonal = ({ form }: StagePersonalProps) => {
  const { t } = useTranslation()
  const {
    register,
    control,
    formState: { errors, dirtyFields },
  } = form

  return (
    <div className="register-fields">
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
        <Input
          autoComplete="name"
          error={errors.fullName?.message}
          valid={!!dirtyFields.fullName && !errors.fullName}
          {...register('fullName')}
        />
      </FieldShell>

      <FieldShell label={t('auth.bio')}>
        <Input
          multiline
          placeholder={t('auth.bioPlaceholder')}
          error={errors.bio?.message}
          valid={!!dirtyFields.bio && !errors.bio}
          {...register('bio')}
        />
      </FieldShell>

      <FieldShell label={t('auth.location')}>
        <Input
          autoComplete="address-level2"
          error={errors.location?.message}
          valid={!!dirtyFields.location && !errors.location}
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
    </div>
  )
}

export default StagePersonal
