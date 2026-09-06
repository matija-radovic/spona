import './PostingFields.scss'
import { Controller, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Dropdown, FieldGroup, FieldShell, Input, TagInput } from '@shared/components'
import {
  JOB_DESCRIPTION_MAX,
  JOB_DESCRIPTION_MIN,
  JOB_KEYWORD_MAX,
  JOB_KEYWORDS_MAX,
  JOB_TITLE_MAX,
  JOB_TITLE_MIN,
} from '@utils/constants/validation'
import {
  EXPERIENCE_LEVEL_VALUES,
  JOB_TYPE_VALUES,
  SALARY_CURRENCY_VALUES,
  SALARY_PERIOD_VALUES,
  WORKING_HOURS_VALUES,
  WORKPLACE_TYPE_VALUES,
  categoryOptions,
  enumOptionsWithEmpty,
  enumOptions,
} from '@pages/jobs/lib/jobEnums'
import type { JobFormData } from '@pages/jobs/lib/jobSchema'

interface PostingFieldsProps {
  register: UseFormRegister<JobFormData>
  control: Control<JobFormData>
  errors: FieldErrors<JobFormData>
}

const PostingFields = ({ register, control, errors }: PostingFieldsProps) => {
  const { t } = useTranslation()

  const unspecified = t('jobs.fields.unspecified')
  const categories = categoryOptions(t)
  const currencies = SALARY_CURRENCY_VALUES.map((value) => ({ value, label: value }))

  return (
    <div className="posting-fields">
      <FieldShell
        label={t('jobs.fields.title')}
        hint={t('jobs.create.lengthHint', { min: JOB_TITLE_MIN, max: JOB_TITLE_MAX })}
        required
      >
        <Input error={errors.title?.message} {...register('title')} />
      </FieldShell>

      <FieldShell
        label={t('jobs.fields.description')}
        hint={t('jobs.create.lengthHint', { min: JOB_DESCRIPTION_MIN, max: JOB_DESCRIPTION_MAX })}
        required
      >
        <Input multiline rows={10} error={errors.description?.message} {...register('description')} />
      </FieldShell>

      <div className="posting-fields-grid">
        <Controller
          control={control}
          name="category"
          render={({ field }) => (
            <FieldShell
              label={t('jobs.fields.category')}
              className="posting-fields-wide"
              required
              htmlLabel={false}
              error={errors.category?.message}
            >
              <Dropdown
                searchable
                label={t('jobs.fields.category')}
                options={categories}
                value={field.value}
                placeholder={unspecified}
                searchPlaceholder={t('jobs.create.categorySearch')}
                noMatch={{
                  option: { value: 'OTHER', label: t('jobs.category.OTHER') },
                  hint: t('jobs.create.categoryNoMatch'),
                }}
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
              required
              htmlLabel={false}
              error={errors.jobType?.message}
            >
              <Dropdown
                label={t('jobs.fields.jobType')}
                options={enumOptionsWithEmpty(JOB_TYPE_VALUES, 'jobs.type', t)}
                value={field.value}
                placeholder={unspecified}
                onChange={(next) => field.onChange(next)}
              />
            </FieldShell>
          )}
        />

        <Controller
          control={control}
          name="workingHours"
          render={({ field }) => (
            <FieldShell
              label={t('jobs.fields.workingHours')}
              htmlLabel={false}
              error={errors.workingHours?.message}
            >
              <Dropdown
                label={t('jobs.fields.workingHours')}
                options={enumOptionsWithEmpty(WORKING_HOURS_VALUES, 'jobs.hours', t)}
                value={field.value}
                placeholder={unspecified}
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
              required
              htmlLabel={false}
              error={errors.workplaceType?.message}
            >
              <Dropdown
                label={t('jobs.fields.workplaceType')}
                options={enumOptionsWithEmpty(WORKPLACE_TYPE_VALUES, 'jobs.workplace', t)}
                value={field.value}
                placeholder={unspecified}
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
                options={enumOptionsWithEmpty(EXPERIENCE_LEVEL_VALUES, 'jobs.experience', t)}
                value={field.value}
                placeholder={unspecified}
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
