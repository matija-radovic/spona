import './PostingFields.scss'
import { Controller, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Dropdown, FieldGroup, FieldShell, Input, TagInput } from '@shared/components'
import { jobCategoryOptions } from '@utils/constants/jobRoles'
import { JOB_KEYWORD_MAX, JOB_KEYWORDS_MAX } from '@utils/constants/validation'
import {
  EXPERIENCE_LEVEL_VALUES,
  JOB_TYPE_VALUES,
  SALARY_CURRENCY_VALUES,
  SALARY_PERIOD_VALUES,
  WORKPLACE_TYPE_VALUES,
  enumOptions,
} from '@pages/jobs/lib/jobEnums'
import type { JobFormData } from '@pages/jobs/views/JobCreate/schema'

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
      <FieldShell label={t('jobs.fields.title')} required>
        <Input error={errors.title?.message} {...register('title')} />
      </FieldShell>

      <FieldShell label={t('jobs.fields.description')} required>
        <Input multiline rows={10} error={errors.description?.message} {...register('description')} />
      </FieldShell>

      <div className="posting-fields-grid">
        <Controller
          control={control}
          name="category"
          render={({ field }) => (
            <FieldShell
              label={t('jobs.fields.category')}
              required
              htmlLabel={false}
              error={errors.category?.message}
            >
              <Dropdown
                label={t('jobs.fields.category')}
                options={categories}
                value={field.value}
                placeholder={t('jobs.fields.category')}
                onChange={(next) => field.onChange(next)}
              />
            </FieldShell>
          )}
        />

        <Controller
          control={control}
          name="jobType"
          render={({ field }) => (
            <FieldShell
              label={t('jobs.fields.jobType')}
              htmlLabel={false}
              error={errors.jobType?.message}
            >
              <Dropdown
                label={t('jobs.fields.jobType')}
                options={enumOptions(JOB_TYPE_VALUES, 'jobs.type', t)}
                value={field.value}
                onChange={(next) => field.onChange(next)}
              />
            </FieldShell>
          )}
        />

        <Controller
          control={control}
          name="workplaceType"
          render={({ field }) => (
            <FieldShell
              label={t('jobs.fields.workplaceType')}
              htmlLabel={false}
              error={errors.workplaceType?.message}
            >
              <Dropdown
                label={t('jobs.fields.workplaceType')}
                options={enumOptions(WORKPLACE_TYPE_VALUES, 'jobs.workplace', t)}
                value={field.value}
                onChange={(next) => field.onChange(next)}
              />
            </FieldShell>
          )}
        />

        <Controller
          control={control}
          name="experienceLevel"
          render={({ field }) => (
            <FieldShell
              label={t('jobs.fields.experienceLevel')}
              htmlLabel={false}
              error={errors.experienceLevel?.message}
            >
              <Dropdown
                label={t('jobs.fields.experienceLevel')}
                options={enumOptions(EXPERIENCE_LEVEL_VALUES, 'jobs.experience', t)}
                value={field.value}
                onChange={(next) => field.onChange(next)}
              />
            </FieldShell>
          )}
        />
      </div>

      <FieldShell label={t('jobs.fields.location')} required>
        <Input error={errors.location?.message} {...register('location')} />
      </FieldShell>

      <Controller
        control={control}
        name="keywords"
        render={({ field }) => (
          <FieldShell
            label={t('jobs.fields.keywords')}
            hint={t('jobs.create.keywordsHint', { max: JOB_KEYWORDS_MAX })}
            error={errors.keywords?.message}
          >
            <TagInput
              value={field.value}
              onChange={field.onChange}
              max={JOB_KEYWORDS_MAX}
              maxLength={JOB_KEYWORD_MAX}
              placeholder={t('jobs.create.keywordPlaceholder')}
              removeLabel={(keyword) => t('jobs.create.keywordRemove', { keyword })}
            />
          </FieldShell>
        )}
      />

      <FieldGroup legend={t('jobs.create.salary')} hint={t('jobs.create.salaryHint')}>
        <div className="posting-fields-salary-row">
          <FieldShell label={t('jobs.fields.salaryMin')}>
            <Input
              inputMode="decimal"
              error={errors.salaryMin?.message}
              {...register('salaryMin')}
            />
          </FieldShell>

          <FieldShell label={t('jobs.fields.salaryMax')}>
            <Input
              inputMode="decimal"
              error={errors.salaryMax?.message}
              {...register('salaryMax')}
            />
          </FieldShell>

          <Controller
            control={control}
            name="salaryCurrency"
            render={({ field }) => (
              <FieldShell label={t('jobs.fields.salaryCurrency')} htmlLabel={false}>
                <Dropdown
                  label={t('jobs.fields.salaryCurrency')}
                  options={currencies}
                  value={field.value}
                  onChange={(next) => field.onChange(next)}
                />
              </FieldShell>
            )}
          />

          <Controller
            control={control}
            name="salaryPeriod"
            render={({ field }) => (
              <FieldShell label={t('jobs.fields.salaryPeriod')} htmlLabel={false}>
                <Dropdown
                  label={t('jobs.fields.salaryPeriod')}
                  options={enumOptions(SALARY_PERIOD_VALUES, 'jobs.period', t)}
                  value={field.value}
                  onChange={(next) => field.onChange(next)}
                />
              </FieldShell>
            )}
          />
        </div>
      </FieldGroup>
    </div>
  )
}

export default PostingFields
