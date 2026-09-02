import './PostingFields.scss'
import { Controller, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Dropdown, TextField } from '@shared/components'
import { jobCategoryOptions } from '@utils/constants/jobRoles'
import {
  EXPERIENCE_LEVEL_VALUES,
  JOB_TYPE_VALUES,
  SALARY_CURRENCY_VALUES,
  SALARY_PERIOD_VALUES,
  WORKPLACE_TYPE_VALUES,
  enumOptions,
} from '@pages/jobs/lib/jobEnums'
import type { JobFormData } from '@pages/jobs/views/JobCreate/schema'
import { FieldRow } from '../FieldRow'
import { KeywordInput } from '../KeywordInput'

interface PostingFieldsProps {
  register: UseFormRegister<JobFormData>
  control: Control<JobFormData>
  errors: FieldErrors<JobFormData>
}

const PostingFields = ({ register, control, errors }: PostingFieldsProps) => {
  const { t } = useTranslation()

  const categories = jobCategoryOptions(t('jobs.category.OTHER'))
  const currencies = SALARY_CURRENCY_VALUES.map((value) => ({ value, label: value }))

  return (
    <div className="posting-fields">
      <TextField
        label={t('jobs.fields.title')}
        required
        error={errors.title?.message}
        {...register('title')}
      />

      <TextField
        label={t('jobs.fields.description')}
        multiline
        rows={10}
        required
        error={errors.description?.message}
        {...register('description')}
      />

      <div className="posting-fields-grid">
        <Controller
          control={control}
          name="category"
          render={({ field }) => (
            <FieldRow label={t('jobs.fields.category')} required error={errors.category?.message}>
              <Dropdown
                label={t('jobs.fields.category')}
                options={categories}
                value={field.value}
                placeholder={t('jobs.fields.category')}
                onChange={(next) => field.onChange(next)}
              />
            </FieldRow>
          )}
        />

        <Controller
          control={control}
          name="jobType"
          render={({ field }) => (
            <FieldRow label={t('jobs.fields.jobType')} error={errors.jobType?.message}>
              <Dropdown
                label={t('jobs.fields.jobType')}
                options={enumOptions(JOB_TYPE_VALUES, 'jobs.type', t)}
                value={field.value}
                onChange={(next) => field.onChange(next)}
              />
            </FieldRow>
          )}
        />

        <Controller
          control={control}
          name="workplaceType"
          render={({ field }) => (
            <FieldRow label={t('jobs.fields.workplaceType')} error={errors.workplaceType?.message}>
              <Dropdown
                label={t('jobs.fields.workplaceType')}
                options={enumOptions(WORKPLACE_TYPE_VALUES, 'jobs.workplace', t)}
                value={field.value}
                onChange={(next) => field.onChange(next)}
              />
            </FieldRow>
          )}
        />

        <Controller
          control={control}
          name="experienceLevel"
          render={({ field }) => (
            <FieldRow label={t('jobs.fields.experienceLevel')} error={errors.experienceLevel?.message}>
              <Dropdown
                label={t('jobs.fields.experienceLevel')}
                options={enumOptions(EXPERIENCE_LEVEL_VALUES, 'jobs.experience', t)}
                value={field.value}
                onChange={(next) => field.onChange(next)}
              />
            </FieldRow>
          )}
        />
      </div>

      <TextField
        label={t('jobs.fields.location')}
        required
        error={errors.location?.message}
        {...register('location')}
      />

      <Controller
        control={control}
        name="keywords"
        render={({ field }) => (
          <KeywordInput
            label={t('jobs.fields.keywords')}
            value={field.value}
            onChange={field.onChange}
            error={errors.keywords?.message}
          />
        )}
      />

      <fieldset className="posting-fields-salary">
        <legend>{t('jobs.create.salary')}</legend>
        <p className="posting-fields-salary-hint">{t('jobs.create.salaryHint')}</p>

        <div className="posting-fields-salary-row">
          <TextField
            label={t('jobs.fields.salaryMin')}
            inputMode="decimal"
            error={errors.salaryMin?.message}
            {...register('salaryMin')}
          />

          <TextField
            label={t('jobs.fields.salaryMax')}
            inputMode="decimal"
            error={errors.salaryMax?.message}
            {...register('salaryMax')}
          />

          <Controller
            control={control}
            name="salaryCurrency"
            render={({ field }) => (
              <FieldRow label={t('jobs.fields.salaryCurrency')}>
                <Dropdown
                  label={t('jobs.fields.salaryCurrency')}
                  options={currencies}
                  value={field.value}
                  onChange={(next) => field.onChange(next)}
                />
              </FieldRow>
            )}
          />

          <Controller
            control={control}
            name="salaryPeriod"
            render={({ field }) => (
              <FieldRow label={t('jobs.fields.salaryPeriod')}>
                <Dropdown
                  label={t('jobs.fields.salaryPeriod')}
                  options={enumOptions(SALARY_PERIOD_VALUES, 'jobs.period', t)}
                  value={field.value}
                  onChange={(next) => field.onChange(next)}
                />
              </FieldRow>
            )}
          />
        </div>
      </fieldset>
    </div>
  )
}

export default PostingFields
