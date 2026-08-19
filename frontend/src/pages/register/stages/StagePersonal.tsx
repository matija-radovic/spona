import { Controller, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { TextField, AvatarPicker, ContactInfoWidget } from '@shared/components'
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

      <TextField
        label={t('auth.fullName')}
        autoComplete="name"
        error={errors.fullName?.message}
        valid={!!dirtyFields.fullName && !errors.fullName}
        {...register('fullName')}
      />

      <TextField
        label={t('auth.bio')}
        multiline
        placeholder={t('auth.bioPlaceholder')}
        error={errors.bio?.message}
        valid={!!dirtyFields.bio && !errors.bio}
        {...register('bio')}
      />

      <TextField
        label={t('auth.location')}
        autoComplete="address-level2"
        error={errors.location?.message}
        valid={!!dirtyFields.location && !errors.location}
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
    </div>
  )
}

export default StagePersonal
