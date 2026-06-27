import { Controller, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { TextField, ContactInfoWidget } from '@shared/components'
import { SLUG_MIN, SLUG_MAX } from '@utils/constants/validation'
import type { OrgData } from '../schema'

interface StageOrganizationProps {
  form: UseFormReturn<OrgData>
}

const StageOrganization = ({ form }: StageOrganizationProps) => {
  const { t } = useTranslation()
  const {
    register,
    control,
    formState: { errors, dirtyFields },
  } = form

  return (
    <div className="register-fields">
      <TextField
        label={t('auth.org.slug')}
        required
        helper={t('auth.org.slugHelper', { min: SLUG_MIN, max: SLUG_MAX })}
        error={errors.slug?.message}
        valid={!!dirtyFields.slug && !errors.slug}
        {...register('slug')}
      />

      <TextField
        label={t('auth.org.displayName')}
        required
        error={errors.displayName?.message}
        valid={!!dirtyFields.displayName && !errors.displayName}
        {...register('displayName')}
      />

      <TextField
        label={t('auth.org.legalName')}
        required
        error={errors.legalName?.message}
        valid={!!dirtyFields.legalName && !errors.legalName}
        {...register('legalName')}
      />

      <TextField
        label={t('auth.org.description')}
        multiline
        error={errors.description?.message}
        valid={!!dirtyFields.description && !errors.description}
        {...register('description')}
      />

      <TextField
        label={t('auth.org.website')}
        type="url"
        placeholder="https://"
        error={errors.website?.message}
        valid={!!dirtyFields.website && !errors.website}
        {...register('website')}
      />

      <TextField
        label={t('auth.org.location')}
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

export default StageOrganization
