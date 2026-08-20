import './CreateOrganization.scss'
import { useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { TextField, AvatarPicker, ContactInfoWidget } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/Button'
import { parseProblem } from '@utils/api/types'
import { createOrganization } from '@utils/api/organizations'
import { uploadAvatar } from '@utils/api/uploads'
import { SLUG_MIN, SLUG_MAX } from '@utils/constants/validation'
import {
  buildCreateOrgSchema,
  CREATE_ORG_DEFAULTS,
  toCreateOrgPayload,
  type CreateOrgData,
} from './schema'

const CreateOrganization = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const schema = useMemo(() => buildCreateOrgSchema(t), [t])
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, dirtyFields, isSubmitting },
  } = useForm<CreateOrgData>({
    resolver: zodResolver(schema),
    mode: 'onChange',
    defaultValues: CREATE_ORG_DEFAULTS,
  })

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      const org = await createOrganization(toCreateOrgPayload(values))
      navigate(`/companies/${org.slug}`, { replace: true })
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
    }
  })

  return (
    <div className="org-create">
      <div className="org-create-card">
        <header className="org-create-head">
          <h1>{t('auth.org.title')}</h1>
          <p className="org-create-sub">{t('auth.org.subtitle')}</p>
        </header>

        <form className="org-create-form" onSubmit={onSubmit} noValidate>
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

          {error && <p className="org-create-error">{error}</p>}

          <div className="org-create-actions">
            <Button
              type="button"
              variant={ButtonVariant.GHOST}
              onClick={() => navigate('/jobs')}
              disabled={isSubmitting}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={isSubmitting}>
              {isSubmitting ? t('auth.creating') : t('auth.org.create')}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default CreateOrganization
